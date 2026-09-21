// D1 - 읽기 폴링. 동시 접속 CONCURRENT 명이 화면별 주기로 폴링할 때의 요청을 재현한다.
//   화면 17 지도(40%): 목록 4초 + 채팅방 id 10초 / 화면 21 대기(35%): 상세 4초 + 채팅방 id 10초 / 화면 24 채팅(25%): 새 메시지 20초(소켓 정상)
//   → 1인당 0.275 RPS. CONCURRENT=100 이면 27.5 RPS
//   k6 run -o experimental-prometheus-rw -e CONCURRENT=100 loadtest/d1-polling.js
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import {
  listRooms, roomDetail, myChatRooms, chatMessagesAfter, openRoom, joinRoom, leaveRoom, finishRoom,
  fillRoom, groupUsers, resolveChatRoomId, 판교역,
} from './lib/api.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const CONCURRENT = Number(__ENV.CONCURRENT || 100);
const GROUPS = Number(__ENV.GROUPS || 20);                  // 앞 절반은 꽉 찬 방(채팅 중), 뒤 절반은 모집 중인 방(지도에 보임)
const RPS = Number(__ENV.RPS || Math.ceil(CONCURRENT * 0.275));

// 요청 비율 = 화면 비중 x 주기의 역수
const MIX = [
  { upTo: 0.364, name: 'list' },      // 0.40 / 4s
  { upTo: 0.682, name: 'detail' },    // 0.35 / 4s
  { upTo: 0.955, name: 'me' },        // 0.75 / 10s
  { upTo: 1.0,   name: 'after' },     // 0.25 / 20s
];

export const options = {
  scenarios: {
    polling: { executor: 'constant-arrival-rate', rate: RPS, timeUnit: '1s', duration: __ENV.DURATION || '5m', preAllocatedVUs: 50, maxVUs: 300 },
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
    else { const r = openRoom(host.token, 3, 판교역, `LT-g${g}`); partyId = r.id; if (partyId) joinRoom(a.token, partyId); }
    if (!partyId) { console.error(`조 ${g} 방 준비 실패`); continue; }
    groups.push({ g, partyId, full, members: full ? 3 : 2, chatRoomId: resolveChatRoomId(host.token, sleep, 5) });
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
  if (kind === 'list') res = listRooms(me.token);
  else if (kind === 'detail') res = roomDetail(me.token, grp.partyId);
  else if (kind === 'after' && grp.chatRoomId) res = chatMessagesAfter(me.token, grp.chatRoomId, 0);
  else res = myChatRooms(me.token);
  check(res, { '200': (r) => r.status === 200 });
}

export function teardown(data) {
  for (const grp of data.groups) {
    const [host, a] = groupUsers(users, grp.g);
    if (grp.full) finishRoom(host.token, grp.partyId);
    else { leaveRoom(a.token, grp.partyId); leaveRoom(host.token, grp.partyId); }
  }
}
