// D1-SSE - 대기 화면이 SSE 로 바뀐 앱의 읽기 부하. d1-polling.js(폴링 baseline)와 같은 CONCURRENT 로 돌려 전후를 비교한다.
//   17 합승 탭(40%)   목록 4초                          → 0.25 RPS
//   21 대기(30%)      SSE 연결 1개 유지, 상세는 신호 올 때만   → 요청 ~0, 연결 1
//   채팅(30%)         새 메시지 20초 + (아래 깔린 21 의 SSE)   → 0.05 RPS, 연결 1
//   → 1인당 약 0.13 RPS + 대기·채팅 화면 사용자(60%)는 SSE 연결 1개. CONCURRENT=100 이면 13 RPS + 연결 60개
//   sse 시나리오의 VU 는 HOLD_SEC 마다 끊고 다시 붙는다 - 앱의 재연결(백그라운드 복귀·네트워크 전환)을 흉내
//   ./k6 run -o experimental-prometheus-rw -e CONCURRENT=100 loadtest/d1-polling-sse.js
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import {
  listRooms, roomDetail, myChatRooms, pollChat, sendChatRest, finishRoom,
  fillRoom, groupUsers, resolveChatRoomId,
} from './lib/api.js';
import { watchParty } from './lib/sse.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const CONCURRENT = Number(__ENV.CONCURRENT || 100);
const GROUPS = Number(__ENV.GROUPS || 20);                       // 전부 꽉 찬 방 - SSE 는 대기 중인 방에 붙는다
const DURATION = __ENV.DURATION || '5m';
const HOLD = Number(__ENV.HOLD_SEC || 60);
const RPS = Number(__ENV.RPS || Math.ceil(CONCURRENT * 0.13));
const SSE_VUS = Number(__ENV.SSE_VUS || Math.ceil(CONCURRENT * 0.6));

const MIX = [
  { upTo: 0.77, name: 'list' },      // 0.40 x 1/4  = 0.100
  { upTo: 0.89, name: 'chat' },      // 0.30 x 1/20 = 0.015
  { upTo: 0.96, name: 'detail' },    // 신호 받은 뒤 재조회 - 방당 변화 빈도로 어림 (전체의 ~7%)
  { upTo: 1.0,  name: 'me' },
];

export const options = {
  scenarios: {
    polling: { executor: 'constant-arrival-rate', rate: RPS, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 30, maxVUs: 200 },
    sse:     { executor: 'constant-vus', vus: SSE_VUS, duration: DURATION, gracefulStop: '20s', exec: 'sseHolder' },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:GET /matching/rooms (viewport)}': ['p(95)<300'],
    'http_req_duration{name:GET /matching/rooms/{id}}': ['p(95)<300'],
    'http_req_duration{name:GET /chat-rooms/me}': ['p(95)<300'],
    sse_errors: ['count==0'],
    sse_connect_ms: ['p(95)<1000'],                                // 구독 → connected. 헤더가 즉시 나가야 한다
    dropped_iterations: ['count==0'],
  },
};

export function setup() {
  const groups = [];
  for (let g = 0; g < GROUPS; g++) {
    const partyId = fillRoom(users, g);
    if (!partyId) { console.error(`조 ${g} 방 준비 실패`); continue; }
    const host = groupUsers(users, g)[0];
    const chatRoomId = resolveChatRoomId(host.token, sleep, 5);
    let cursor = null;
    if (chatRoomId) { const sent = sendChatRest(host.token, chatRoomId, 'lt:seed'); if (sent.status === 201) cursor = sent.json('id'); }
    groups.push({ g, partyId, chatRoomId, cursor });
  }
  console.log(`방 ${groups.length}개 준비, ${RPS} RPS + SSE ${SSE_VUS}연결 (동시 접속 ${CONCURRENT}명 가정)`);
  return { groups };
}

export default function (data) {
  const grp = data.groups[Math.floor(Math.random() * data.groups.length)];
  const me = users[grp.g * 3 + Math.floor(Math.random() * 3)];
  const pick = Math.random();
  const kind = MIX.find((m) => pick < m.upTo).name;
  let res;
  if (kind === 'list') res = listRooms(me.token);
  else if (kind === 'chat' && grp.chatRoomId) res = pollChat(me.token, grp.chatRoomId, grp.cursor);
  else if (kind === 'detail') res = roomDetail(me.token, grp.partyId);
  else res = myChatRooms(me.token);
  check(res, { '200': (r) => r.status === 200 });
}

export function sseHolder(data) {
  const grp = data.groups[(__VU - 1) % data.groups.length];
  const me = users[grp.g * 3 + ((__VU - 1) % 3)];                   // 그 방의 실제 멤버만 구독할 수 있다
  const r = watchParty(me.token, grp.partyId, { holdSec: HOLD });
  check(r, { 'SSE 200': (x) => x.status === 200 });
  sleep(1 + Math.random() * 2);                                    // 재연결 사이 간격
}

export function teardown(data) {
  for (const grp of data.groups) finishRoom(groupUsers(users, grp.g)[0].token, grp.partyId);
}
