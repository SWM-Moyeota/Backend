// D7 - SSE 동시 연결 한계. 대기자 수만큼 연결이 생기는 구조라 새 병목 후보다.
//   holders : 연결을 CONNS/3 → 2/3 → CONNS 로 올리며 각 VU 가 자기 방의 /events 를 HOLD_SEC 붙잡는다 (끊고 재연결 반복)
//   churn   : 초당 CHURN 건, 무작위 방에서 한 명이 나갔다 다시 들어온다 → 그 방 다른 구독자들에게 member-left·member-joined 가 간다
//             (나간 본인은 서버가 연결을 끊는다). holder 는 앱처럼 변경분을 version 순서대로 목록에 반영하고,
//             번호가 빠지는 등 화면을 믿을 수 없을 때만 방 상세를 다시 읽는다(resync)
//   settle  : churn 이 멈추고 SETTLE 초 뒤(CHECK_AT) 처음 오는 ping 에서, 그 시각을 걸친 hold 를 닫고 방 상세와 맞춘다(sse_view_check).
//             churn 중에 끝난 hold 는 비교하지 않는다 - 도착 중인 변경분 때문에 거짓 경보가 난다.
//             서버 ping 은 전역 30초 주기(모든 연결이 같은 순간)라 hold 는 그때만 닫힌다 - 마지막 단계를 그 ping 까지 늘린다
//   propagation : 측정 전용 방 PROBE_VUS 개(정원 3, 방장 watcher 만 있음)에서 전파 지연을 잰다. holders 와 같은 구간 동안 반복:
//             watcher 구독 → connected 콜백 안에서 t0 → prober join(동기 http) → prober 의 member-joined 를 읽은 시각 t1
//             → sse_propagation_ms = t1 - t0 → 구독 종료 → prober leave. 한 VU 가 두 시각을 찍어 시계 차이가 없다.
//             t0 은 join 요청 직전이라 값에 join 응답 시간이 들어간다. 서버는 커밋 직후(응답 전) 발행하지만 콜백이 http 로 막힌 동안
//             이벤트를 못 읽으므로 실제 전파보다 약간 크게(보수적으로) 나온다. join 실패·미도착은 sse_propagation_miss{reason}
//   본다: sse_connect_ms(헤더 즉시 나가나), sse_events{name:member-*}(churn 이 구독자에게 닿나), sse_stale_event(이미 반영된 변경분),
//         sse_resync{reason}·sse_resync_result{result}(번호 빠짐 등으로 상세 재조회), sse_errors,
//         sse_view_check(result:match 말고는 0 이어야 한다), sse_propagation_ms(부하 단계별 전파 지연),
//         Grafana: process_open_fds · jvm_memory_used_bytes · tomcat_threads_busy(연결이 스레드를 잡지 않는지) · redis 명령 수
//   ./k6 run -o experimental-prometheus-rw -e CONNS=300 loadtest/d7-sse-connections.js      (기본 3단계 x (1m 램프 + 2m 유지) + settle ≈ 10분)
//   짧게: -e CONNS=30 -e RAMP=10s -e PLATEAU=20s -e HOLD_SEC=30      (CHURN_FOR 를 주지 않으면 마지막 단계 끝까지 돈다)
//   사용자: holders·churn 은 0..CONNS-1, propagation 은 CONNS.. 부터 방마다 2명(watcher, prober) - users.json 에 CONNS + 2*PROBE_VUS 명. 끄려면 -e PROBE_VUS=0
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { leaveRoom, joinRoom, finishRoom, fillRoom, groupUsers, roomDetail, breakdown, openRoom, spotOf, userInfo } from './lib/api.js';
import {
  watchParty, newRoomView, follow, compareWithDetail, VIEW_CHECK_RESULTS, RESYNC_REASONS, RESYNC_RESULTS, propagationMs,
} from './lib/sse.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const CONNS = Number(__ENV.CONNS || 300);                    // 3의 배수. users.json 에 CONNS 명 이상
const PROBE_VUS = Number(__ENV.PROBE_VUS || 5);              // 전파 지연 측정 방(= VU) 수. 0 이면 propagation 을 끈다
const PROBE_TIMEOUT = Number(__ENV.PROBE_TIMEOUT_SEC || 10); // join 뒤 이만큼 member-joined 가 없으면 미도착 - 판정은 그 뒤 첫 ping 에서(최대 +30초)
if (users.length < CONNS + 2 * PROBE_VUS) throw new Error(`users.json 에 ${users.length}명뿐이다 - CONNS=${CONNS} PROBE_VUS=${PROBE_VUS} 는 ${CONNS + 2 * PROBE_VUS}명 필요 (PROBE_VUS=0 이면 ${CONNS}명)`);
const HOLD = Number(__ENV.HOLD_SEC || 90);
const CHURN = Number(__ENV.CHURN || 2);                      // 초당 나갔다 들어오기 건수
const ROOMS = CONNS / 3;
const RAMP = __ENV.RAMP || '1m';                            // 단계 올리는 시간
const PLATEAU = __ENV.PLATEAU || '2m';                      // 각 단계 유지 시간 (마지막 단계는 settle 까지 늘어난다)
const SETTLE = Number(__ENV.SETTLE || 10);                  // churn 이 멈춘 뒤 비교 전까지 조용히 듣는 시간 - 도착 중인 변경분이 다 닿게
const HEARTBEAT = 30;                                       // 서버 ping 주기(PartySseRegistry, 전역 fixedRate)

/** '1m30s' · '90s' · '90' → 초 */
function sec(v) {
  if (/^\d+(\.\d+)?$/.test(v)) return Number(v);
  const re = /(\d+(?:\.\d+)?)([hms])/g;
  let total = 0;
  for (let m = re.exec(v); m; m = re.exec(v)) total += Number(m[1]) * { h: 3600, m: 60, s: 1 }[m[2]];
  return total;
}
// 시간 구성(초, 시나리오 시작 기준): churn [CHURN_START, CHURN_END) → 조용히 SETTLE → CHECK_AT 뒤 첫 ping 에서 비교 → 램프 다운
const CHURN_START = 30;
const BEFORE_LAST = 3 * sec(RAMP) + 2 * sec(PLATEAU);      // 마지막 단계(유지)가 시작하는 시각
const CHURN_END = CHURN_START + Math.max(1, __ENV.CHURN_FOR ? sec(__ENV.CHURN_FOR) : BEFORE_LAST + sec(PLATEAU) - CHURN_START);   // 기본: 원래 마지막 단계 끝까지
const CHECK_AT = CHURN_END + SETTLE;
const LAST_PLATEAU = Math.max(sec(PLATEAU), CHECK_AT + HEARTBEAT + 10 - BEFORE_LAST);   // 첫 ping + 상세 조회(흩뿌림 5초)까지
const CHECK = 'GET /matching/rooms/{id} (check)';           // 측정용 상세 조회 - 지표에서 따로 잡힌다
const churnOutcome = new Counter('churn');                   // result 태그: ok / full / busy / other
const reconnectRetry = new Counter('sse_reconnect_retry');   // churn 이 내 자리를 뺀 순간(leave~join 0.5초)에 재연결해 403 → 잠시 뒤 재시도
const propagationMiss = new Counter('sse_propagation_miss'); // reason 태그: connect(구독·connected 실패) / join(참여 실패, 409 등) / timeout(이벤트 미도착)
const PROPAGATION_MISS_REASONS = ['connect', 'join', 'timeout'];
const HOLDERS_END = BEFORE_LAST + LAST_PLATEAU;             // holders 램프 다운 직전 - propagation 은 0 ~ 여기까지 돈다

/** 측정 방 p 의 사용자 - holders·churn 이 쓰는 0..CONNS-1 뒤에 방마다 2명 */
function probeUsers(p) { return { watcher: users[CONNS + 2 * p], prober: users[CONNS + 2 * p + 1] }; }

export const options = {
  scenarios: {
    ...(PROBE_VUS > 0 ? {
      propagation: {
        executor: 'constant-vus', exec: 'propagation', vus: PROBE_VUS, duration: `${HOLDERS_END}s`, gracefulStop: '45s',   // 미도착 판정(+ping 30초)까지 기다린다
      },
    } : {}),
    holders: {
      executor: 'ramping-vus', exec: 'holder', startVUs: 3, gracefulRampDown: '30s', gracefulStop: '30s',
      stages: [
        { target: Math.ceil(CONNS / 3), duration: RAMP }, { target: Math.ceil(CONNS / 3), duration: PLATEAU },
        { target: Math.ceil(CONNS * 2 / 3), duration: RAMP }, { target: Math.ceil(CONNS * 2 / 3), duration: PLATEAU },
        { target: CONNS, duration: RAMP }, { target: CONNS, duration: `${LAST_PLATEAU}s` },
        { target: 0, duration: '30s' },
      ],
    },
    churn: {
      executor: 'constant-arrival-rate', exec: 'churner', rate: CHURN, timeUnit: '1s', duration: `${CHURN_END - CHURN_START}s`,
      preAllocatedVUs: 5, maxVUs: 30, startTime: `${CHURN_START}s`,
    },
  },
  thresholds: {
    sse_errors: ['count==0'],
    sse_connect_ms: ['p(95)<1000'],
    'churn{result:other}': ['count==0'],
    http_req_failed: ['rate<0.01'],
    ...breakdown('sse_view_check', 'result', VIEW_CHECK_RESULTS),   // 측정만 - 권장 기준은 README
    ...breakdown('sse_resync', 'reason', RESYNC_REASONS),
    ...breakdown('sse_resync_result', 'result', RESYNC_RESULTS),
    ...(PROBE_VUS > 0 ? breakdown('sse_propagation_miss', 'reason', PROPAGATION_MISS_REASONS) : {}),
  },
};

export function setup() {
  const rooms = [];
  for (let g = 0; g < ROOMS; g++) {
    const partyId = fillRoom(users, g);
    if (partyId) rooms.push({ g, partyId });
    else console.error(`조 ${g} 방 준비 실패`);
  }
  // 측정 방: 방장(watcher)만 들어간 정원 3 방. prober 의 publicId 는 member-joined 를 가려내는 데 쓴다
  const probes = [];
  for (let p = 0; p < PROBE_VUS; p++) {
    const { watcher, prober } = probeUsers(p);
    const info = userInfo(prober.token);
    const from = spotOf(ROOMS + p);
    const r = openRoom(watcher.token, 3, from.dest, `LT-probe${p}`, from);
    if (info.status === 200 && r.id) probes.push({ p, partyId: r.id, proberId: info.json('uuid') });
    else console.error(`측정 방 ${p} 준비 실패: info ${info.status}, 방 생성 ${r.status} ${r.body}`);
  }
  console.log(`방 ${rooms.length}개, 최대 연결 ${CONNS}, churn ${CHURN}/s ${CHURN_START}~${CHURN_END}초, 비교 ${CHECK_AT}초 뒤 첫 ping, 측정 방 ${probes.length}개 0~${HOLDERS_END}초`);
  return { rooms, probes };
}

/**
 * 자기 조 방을 HOLD 동안 듣는다. 앱처럼 connected 로 목록을 받고 변경분을 version 순서대로 반영한다 - 방 상세는 resync 때만 다시 읽는다.
 * CHECK_AT 을 걸치는 hold 는 거기서 닫도록 줄이고, 닫힌 뒤(churn 이 멈춘 뒤) 한 번만 상세와 맞춘다
 */
export function holder(data) {
  const n = data.rooms.length;
  const room = data.rooms[(__VU - 1) % n];
  // 방마다 VU 순서대로 조원 0 → 1 → 2. 같은 사람이 같은 방을 두 번 구독하면 서버가 옛 연결을 닫으니 겹치지 않게 한다
  const me = users[room.g * 3 + (Math.floor((__VU - 1) / n) % 3)];
  const view = newRoomView();
  const t0 = (Date.now() - exec.scenario.startTime) / 1000;
  const holdSec = t0 < CHECK_AT && t0 + HOLD > CHECK_AT ? CHECK_AT - t0 : HOLD;
  const started = Date.now();
  const r = watchParty(me.token, room.partyId, {
    holdSec,
    onEvent: (name, ev) => { follow(view, me.token, room.partyId, name, ev); return true; },
  });
  if (r.status === 403) {                                    // churner 가 나를 잠깐 내보낸 사이 - 앱이면 다시 붙어 connected 로 목록을 새로 받는다
    reconnectRetry.add(1);
    sleep(1);
    return;
  }
  if (r.status !== 200) console.error(`holder vu=${__VU} party=${room.partyId} status=${r.status} closedBy=${r.closedBy}`);
  check(r, { 'SSE 200': (x) => x.status === 200 });
  // ping 으로 닫혔고(서버가 끊은 게 아니다) CHECK_AT 전에 시작해 그 뒤에 끝난 hold 만 - 모두 같은 ping 에서 닫히니 조회는 흩는다
  const ended = (Date.now() - exec.scenario.startTime) / 1000;
  if (r.closedBy === 'hold' && Date.now() - started >= holdSec * 1000 && t0 < CHECK_AT && ended >= CHECK_AT) {
    sleep(Math.random() * 5);
    compareWithDetail(view, roomDetail(me.token, room.partyId, CHECK));
  }
  sleep(1 + Math.random() * 2);
}

/** 무작위 방의 참여자(방장 아님) 한 명이 나갔다 다시 들어온다 - 그 방 구독자에게 member-left, member-joined 가 하나씩 간다 */
export function churner(data) {
  const room = data.rooms[Math.floor(Math.random() * data.rooms.length)];
  const who = groupUsers(users, room.g)[1 + Math.floor(Math.random() * 2)];
  const l = leaveRoom(who.token, room.partyId);
  if (l.status === 403 && String(l.body).includes('NOT_PARTY_MEMBER')) { churnOutcome.add(1, { result: 'busy' }); return; }   // 다른 churner 가 먼저 내보낸 사람 - 정상
  if (l.status !== 204 && l.status !== 409) { churnOutcome.add(1, { result: 'other' }); console.error(`leave ${l.status}: ${l.body}`); return; }
  sleep(0.5);
  const j = joinRoom(who.token, room.partyId);
  if (j.status === 200) churnOutcome.add(1, { result: 'ok' });
  else if (j.status === 409) churnOutcome.add(1, { result: 'full' });               // 다른 churner 와 겹침 - 정상
  else { churnOutcome.add(1, { result: 'other' }); console.error(`join ${j.status}: ${j.body}`); }
}

// VU 마다 측정 방 하나. k6 는 시나리오 안 VU 번호를 주지 않아 첫 iteration 번호로 나눠 갖는다 - PROBE_VUS 개가 동시에 시작해 0..PROBE_VUS-1 을 받는다
let probeSlot = null;
let probeWarned = 0;

/** watcher 로 구독 → connected 에서 t0 + prober join → prober 의 member-joined 에서 t1. 끝나면 prober 를 내보내 원상복구 */
export function propagation(data) {
  if (probeSlot === null) probeSlot = exec.scenario.iterationInTest;
  const room = data.probes[probeSlot];
  if (!room) { sleep(5); return; }                          // 측정 방 준비 실패 - 다른 VU 의 방을 건드리지 않는다
  const { watcher, prober } = probeUsers(room.p);
  let t0 = 0;
  let joined = null;                                         // join 응답 status. null 이면 connected 전에 끝났다
  let arrived = false;
  const r = watchParty(watcher.token, room.partyId, {
    holdSec: PROBE_TIMEOUT,
    onEvent: (name, ev) => {
      if (name === 'connected' && joined === null) {
        t0 = Date.now();
        joined = joinRoom(prober.token, room.partyId).status;   // 동기 - 끝날 때까지 이 구독의 이벤트를 못 읽는다
        return joined === 200;
      }
      if (name === 'member-joined' && joined === 200 && ev && ev.member && ev.member.publicId === room.proberId) {
        propagationMs.add(Date.now() - t0);
        arrived = true;
        return false;
      }
      return true;
    },
  });
  const reason = joined === null ? 'connect' : joined !== 200 ? 'join' : arrived ? null : 'timeout';
  if (reason) {
    propagationMiss.add(1, { reason });
    if (probeWarned++ < 5) console.warn(`propagation party=${room.partyId} ${reason}: sse ${r.status} ${r.closedBy}, join ${joined}`);
  }
  if (joined === 200) {
    const l = leaveRoom(prober.token, room.partyId);
    if (l.status !== 204) console.error(`propagation leave ${l.status}: ${l.body}`);
  }
  sleep(1 + Math.random());
}

export function teardown(data) {
  for (const room of data.rooms) finishRoom(groupUsers(users, room.g)[0].token, room.partyId);
  // 측정 방은 ACTIVE 라 finish 가 안 된다(PARTY_NOT_COMPLETED) - 남아 있는 prober 를 내보내고 방장이 나가 CANCELED 로 닫는다
  for (const room of data.probes || []) {
    const { watcher, prober } = probeUsers(room.p);
    const d = roomDetail(watcher.token, room.partyId);
    if (d.status === 200 && (d.json('members') || []).some((m) => m.publicId === room.proberId)) leaveRoom(prober.token, room.partyId);
    leaveRoom(watcher.token, room.partyId);
  }
}
