// D1 - 읽기 폴링(옛 앱 기준선). 대기 화면이 SSE 로 바뀌기 전 앱의 화면별 폴링을 재현한다 - 지금 앱의 읽기 부하는 d1-polling-sse.js 다.
//   SSE 전환 전후를 비교할 때만 돌린다.
//   17 합승 탭(40%)            목록 4초                          → 0.25 RPS
//   21 대기(30%)               상세 4초                          → 0.25 RPS
//   채팅 열어 둠(30%)          상세 4초(아래 깔린 21) + 새 메시지 20초 → 0.30 RPS
//   탭 이동·앱 복귀             /chat-rooms/me, 상세 1회           → 전체의 약 3%
//   → 1인당 약 0.27 RPS. CONCURRENT=100 이면 27 RPS.   ※ 1차 배포에선 /chat-rooms/me 10초 폴링이 돌지 않는다(대기 단계에선 건너뜀)
//   k6 run -o experimental-prometheus-rw -e CONCURRENT=100 loadtest/d1-polling.js
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import {
  listRooms, roomDetail, myChatRooms, pollChat, sendChatRest, openRoom, joinRoom, disbandRoom,
  fillRoom, groupUsers, resolveChatRoomId, 판교역, spotOf, viewportAround,
} from './lib/api.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const CONCURRENT = Number(__ENV.CONCURRENT || 100);
const GROUPS = Number(__ENV.GROUPS || 20);                  // 앞 절반은 꽉 찬 방(채팅 중), 뒤 절반은 모집 중인 방(지도에 보임)
const RPS = Number(__ENV.RPS || Math.ceil(CONCURRENT * 0.27));

// 요청 비율 = 화면 비중 x 주기의 역수
const MIX = [
  { upTo: 0.36, name: 'list' },       // 0.40 x 1/4
  { upTo: 0.91, name: 'detail' },     // 0.60 x 1/4
  { upTo: 0.97, name: 'chat' },       // 0.30 x 1/20
  { upTo: 1.0,  name: 'me' },         // 탭 이동·앱 복귀
];

export const options = {
  scenarios: {
    polling: { executor: 'constant-arrival-rate', rate: RPS, timeUnit: '1s', duration: __ENV.DURATION || '5m', preAllocatedVUs: 200, maxVUs: 1000 },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:GET /matching/rooms (viewport)}': ['p(95)<300'],
    'http_req_duration{name:GET /matching/rooms/{id}}': ['p(95)<300'],
    'http_req_duration{name:GET /chat-rooms/me}': ['p(95)<300'],
    'http_req_duration{name:GET /chat-rooms/{id}/messages/after}': ['p(95)<300'],
    dropped_iterations: ['count==0'],
  },
};

export function setup() {
  const groups = [];
  for (let g = 0; g < GROUPS; g++) {
    const full = g < GROUPS / 2;
    const [host, a] = groupUsers(users, g);
    let partyId;
    if (full) partyId = fillRoom(users, g);
    else { const from = spotOf(g); const r = openRoom(host.token, 3, from.dest, `LT-g${g}`, from); partyId = r.id; if (partyId) joinRoom(a.token, partyId); }
    if (!partyId) { console.error(`조 ${g} 방 준비 실패`); continue; }
    const chatRoomId = resolveChatRoomId(host.token, sleep, 5);
    // 폴링 커서로 쓸 메시지를 하나 심는다 - cursor 없이는 after 를 못 부른다(서버가 cursor < 1 을 400 으로 막는다)
    let cursor = null;
    if (chatRoomId) { const sent = sendChatRest(host.token, chatRoomId, 'lt:seed'); if (sent.status === 201) cursor = sent.json('id'); }
    groups.push({ g, partyId, full, members: full ? 3 : 2, chatRoomId, cursor });
  }
  console.log(`방 ${groups.length}개 준비, 목표 ${RPS} RPS (동시 접속 ${CONCURRENT}명 가정)`);
  return { groups };
}

export default function (data) {
  const grp = data.groups[Math.floor(Math.random() * data.groups.length)];
  const me = users[grp.g * 3 + Math.floor(Math.random() * grp.members)];   // 그 방의 실제 멤버로 요청한다
  const pick = Math.random();
  const kind = MIX.find((m) => pick < m.upTo).name;
  let res;
  if (kind === 'list') res = listRooms(me.token, viewportAround(spotOf(grp.g)));   // 그 조가 서 있는 곳 주변
  else if (kind === 'detail') res = roomDetail(me.token, grp.partyId);
  else if (kind === 'chat' && grp.chatRoomId) res = pollChat(me.token, grp.chatRoomId, grp.cursor);
  else res = myChatRooms(me.token);
  check(res, { '200': (r) => r.status === 200 });
}

export function teardown(data) {
  for (const grp of data.groups) disbandRoom(groupUsers(users, grp.g).slice(0, grp.members), grp.partyId);
}
