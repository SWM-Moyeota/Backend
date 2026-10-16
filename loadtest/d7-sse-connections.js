// D7 - SSE 동시 연결 한계. 대기자 수만큼 연결이 생기는 구조라 새 병목 후보다.
//   holders : 연결을 CONNS/3 → 2/3 → CONNS 로 올리며 각 VU 가 자기 방의 /events 를 HOLD_SEC 붙잡는다 (끊고 재연결 반복)
//   churn   : 초당 CHURN 건, 무작위 방에서 한 명이 나갔다 다시 들어온다 → 그 방 다른 구독자들에게 member-left·member-joined 가 간다
//             (나간 본인은 서버가 연결을 끊는다). holder 는 앱처럼 변경분을 목록에 더하고 뺄 뿐 방 상세를 다시 읽지 않는다
//   settle  : churn 이 멈추고 SETTLE 초 뒤(CHECK_AT) 처음 오는 ping 에서, 그 시각을 걸친 hold 를 닫고 방 상세와 맞춘다(sse_view_check).
//             churn 중에 끝난 hold 는 비교하지 않는다 - 도착 중인 변경분 때문에 거짓 경보가 난다.
//             서버 ping 은 전역 30초 주기(모든 연결이 같은 순간)라 hold 는 그때만 닫힌다 - 마지막 단계를 그 ping 까지 늘린다
//   본다: sse_connect_ms(헤더 즉시 나가나), sse_member_deltas(churn 이 구독자에게 닿나. applied:no 는 중복·순서 꼬임), sse_errors,
//         sse_view_check(result:members·status 는 0 이어야 한다),
//         Grafana: process_open_fds · jvm_memory_used_bytes · tomcat_threads_busy(연결이 스레드를 잡지 않는지) · redis 명령 수
//   ./k6 run -o experimental-prometheus-rw -e CONNS=300 loadtest/d7-sse-connections.js      (기본 3단계 x (1m 램프 + 2m 유지) + settle ≈ 10분)
//   짧게: -e CONNS=30 -e RAMP=10s -e PLATEAU=20s -e HOLD_SEC=30      (CHURN_FOR 를 주지 않으면 마지막 단계 끝까지 돈다)
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { leaveRoom, joinRoom, finishRoom, fillRoom, groupUsers, roomDetail, breakdown } from './lib/api.js';
import { watchParty, newRoomView, applyEvent, compareWithDetail, VIEW_CHECK_RESULTS } from './lib/sse.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const CONNS = Number(__ENV.CONNS || 300);                    // 3의 배수. users.json 에 CONNS 명 이상
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
const churnOutcome = new Counter('churn');                   // result 태그: ok / full / other
const reconnectRetry = new Counter('sse_reconnect_retry');   // churn 이 내 자리를 뺀 순간(leave~join 0.5초)에 재연결해 403 → 잠시 뒤 재시도
const memberDeltas = new Counter('sse_member_deltas');      // 받은 member-joined/left. applied 태그: yes(목록이 바뀜) / no(이미 반영된 변경분)

export const options = {
  scenarios: {
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
  },
};

export function setup() {
  const rooms = [];
  for (let g = 0; g < ROOMS; g++) {
    const partyId = fillRoom(users, g);
    if (partyId) rooms.push({ g, partyId });
    else console.error(`조 ${g} 방 준비 실패`);
  }
  console.log(`방 ${rooms.length}개, 최대 연결 ${CONNS}, churn ${CHURN}/s ${CHURN_START}~${CHURN_END}초, 비교 ${CHECK_AT}초 뒤 첫 ping`);
  return { rooms };
}

/**
 * 자기 조 방을 HOLD 동안 듣는다. 앱처럼 connected 로 목록을 받고 변경분을 더하고 뺀다 - 방 상세는 다시 읽지 않는다.
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
    onEvent: (name, ev) => {
      if (name === 'member-joined' || name === 'member-left') {
        const before = view.members.size;
        applyEvent(view, name, ev);
        memberDeltas.add(1, { name, applied: view.members.size !== before ? 'yes' : 'no' });
      } else {
        applyEvent(view, name, ev);
      }
      return true;
    },
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
  if (l.status !== 204 && l.status !== 409) { churnOutcome.add(1, { result: 'other' }); console.error(`leave ${l.status}: ${l.body}`); return; }
  sleep(0.5);
  const j = joinRoom(who.token, room.partyId);
  if (j.status === 200) churnOutcome.add(1, { result: 'ok' });
  else if (j.status === 409) churnOutcome.add(1, { result: 'full' });               // 다른 churner 와 겹침 - 정상
  else { churnOutcome.add(1, { result: 'other' }); console.error(`join ${j.status}: ${j.body}`); }
}

export function teardown(data) {
  for (const room of data.rooms) finishRoom(groupUsers(users, room.g)[0].token, room.partyId);
}
