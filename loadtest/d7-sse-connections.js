// D7 - SSE 동시 연결 한계. 대기자 수만큼 연결이 생기는 구조라 새 병목 후보다.
//   holders : 연결을 CONNS/3 → 2/3 → CONNS 로 올리며 각 VU 가 자기 방의 /events 를 HOLD_SEC 붙잡는다 (끊고 재연결 반복)
//   churn   : 초당 CHURN 건, 무작위 방에서 한 명이 나갔다 다시 들어온다 → 그 방 다른 구독자들에게 member-left·member-joined 가 간다
//             (나간 본인은 서버가 연결을 끊는다). holder 는 앱처럼 변경분을 목록에 더하고 뺄 뿐 방 상세를 다시 읽지 않는다
//   본다: sse_connect_ms(헤더 즉시 나가나), sse_member_deltas(churn 이 구독자에게 닿나. applied:no 는 중복·순서 꼬임), sse_errors,
//         Grafana: process_open_fds · jvm_memory_used_bytes · tomcat_threads_busy(연결이 스레드를 잡지 않는지) · redis 명령 수
//   ./k6 run -o experimental-prometheus-rw -e CONNS=300 loadtest/d7-sse-connections.js      (기본 3단계 x (1m 램프 + 2m 유지) ≈ 10분)
//   짧게: -e CONNS=30 -e RAMP=10s -e PLATEAU=20s -e CHURN_FOR=100s -e HOLD_SEC=30
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';
import { leaveRoom, joinRoom, finishRoom, fillRoom, groupUsers } from './lib/api.js';
import { watchParty, newRoomView, applyEvent } from './lib/sse.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const CONNS = Number(__ENV.CONNS || 300);                    // 3의 배수. users.json 에 CONNS 명 이상
const HOLD = Number(__ENV.HOLD_SEC || 90);
const CHURN = Number(__ENV.CHURN || 2);                      // 초당 나갔다 들어오기 건수
const ROOMS = CONNS / 3;
const RAMP = __ENV.RAMP || '1m';                            // 단계 올리는 시간
const PLATEAU = __ENV.PLATEAU || '2m';                      // 각 단계 유지 시간 (마지막 단계는 +1m)
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
        { target: CONNS, duration: RAMP }, { target: CONNS, duration: PLATEAU },
        { target: 0, duration: '30s' },
      ],
    },
    churn: {
      executor: 'constant-arrival-rate', exec: 'churner', rate: CHURN, timeUnit: '1s', duration: __ENV.CHURN_FOR || '10m',
      preAllocatedVUs: 5, maxVUs: 30, startTime: '30s',
    },
  },
  thresholds: {
    sse_errors: ['count==0'],
    sse_connect_ms: ['p(95)<1000'],
    'churn{result:other}': ['count==0'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  const rooms = [];
  for (let g = 0; g < ROOMS; g++) {
    const partyId = fillRoom(users, g);
    if (partyId) rooms.push({ g, partyId });
    else console.error(`조 ${g} 방 준비 실패`);
  }
  console.log(`방 ${rooms.length}개, 최대 연결 ${CONNS}, churn ${CHURN}/s`);
  return { rooms };
}

/** 자기 조 방을 HOLD 동안 듣는다. 앱처럼 connected 로 목록을 받고 변경분을 더하고 뺀다 - 방 상세는 다시 읽지 않는다 */
export function holder(data) {
  const n = data.rooms.length;
  const room = data.rooms[(__VU - 1) % n];
  // 방마다 VU 순서대로 조원 0 → 1 → 2. 같은 사람이 같은 방을 두 번 구독하면 서버가 옛 연결을 닫으니 겹치지 않게 한다
  const me = users[room.g * 3 + (Math.floor((__VU - 1) / n) % 3)];
  const view = newRoomView();
  const r = watchParty(me.token, room.partyId, {
    holdSec: HOLD,
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
