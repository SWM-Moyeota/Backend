// D4 - 채팅 WebSocket. 3명짜리 채팅방에 연결을 CONNS 개까지 올리며 전달 지연과 연결 한계를 본다.
//   MODE=ws(기본): 앱의 채팅 화면 그대로 - 방 열기 3건 → 소켓 + 12초마다 메시지 + 읽음 + 폴링(첫 수신 전 3초, 후 20초) + 아래 깔린 21 의 상세 4초
//   MODE=fallback: 소켓이 죽어 전원이 3초 폴링 + REST 전송으로 떨어진 상황 (서버 재시작 직후)
//   k6 run -o experimental-prometheus-rw -e CONNS=150 loadtest/d4-chat-ws.js
//   k6 run -o experimental-prometheus-rw -e CONNS=150 -e MODE=fallback loadtest/d4-chat-ws.js
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { fillRoom, groupUsers, finishRoom, resolveChatRoomId, openChatRoom, pollChat, lastMessageId, roomDetail, sendChatRest } from './lib/api.js';
import { chatSession } from './lib/stomp.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const CONNS = Number(__ENV.CONNS || 150);                   // 3의 배수
const MODE = __ENV.MODE || 'ws';
const HOLD = Number(__ENV.HOLD_SEC || 60);
const latency = new Trend('chat_delivery_ms', true);
const connected = new Counter('ws_connected');
const wsErrors = new Counter('ws_errors');

export const options = {
  scenarios: {
    chat: {
      executor: 'ramping-vus', startVUs: 3, gracefulRampDown: '90s', gracefulStop: '90s',
      stages: [
        { target: Math.ceil(CONNS / 3), duration: '1m' }, { target: Math.ceil(CONNS / 3), duration: '2m' },
        { target: Math.ceil(CONNS * 2 / 3), duration: '1m' }, { target: Math.ceil(CONNS * 2 / 3), duration: '2m' },
        { target: CONNS, duration: '1m' }, { target: CONNS, duration: '3m' },
        { target: 0, duration: '30s' },
      ],
    },
  },
  thresholds: MODE === 'ws'
    ? { chat_delivery_ms: ['p(95)<500'], ws_errors: ['count==0'], http_req_failed: ['rate<0.01'] }
    : { 'http_req_duration{name:GET /chat-rooms/{id}/messages/after}': ['p(95)<300'], 'http_req_duration{name:GET /chat-rooms/{id}/messages}': ['p(95)<300'], http_req_failed: ['rate<0.01'] },
};

export function setup() {
  const rooms = {};
  for (let g = 0; g < CONNS / 3; g++) {
    const partyId = fillRoom(users, g);
    if (!partyId) { console.error(`조 ${g} 방 준비 실패`); continue; }
    rooms[g] = { partyId, chatRoomId: resolveChatRoomId(groupUsers(users, g)[0].token, sleep, 10) };
  }
  return { rooms };
}

export default function (data) {
  const g = Math.floor((__VU - 1) / 3);
  const room = data.rooms[g];
  if (!room || !room.chatRoomId) { sleep(5); return; }
  const me = users[__VU - 1];

  const opened = openChatRoom(me.token, room.chatRoomId);      // 방 열기: 첫 페이지 · 참여자 · 방 정보
  check(opened, { '채팅방 열기 200': (o) => o.status === 200 });

  if (MODE === 'fallback') {                                   // 소켓 없이 3초 폴링 + REST 전송, 상세 4초 폴링은 그대로
    let cursor = opened.cursor;
    for (let t = 0; t < HOLD; t++) {
      if (t % 3 === 0) { const res = pollChat(me.token, room.chatRoomId, cursor); check(res, { '폴링 200': (r) => r.status === 200 }); cursor = lastMessageId(res, cursor); }
      if (t % 4 === 0) roomDetail(me.token, room.partyId);
      if (t % 12 === 0) sendChatRest(me.token, room.chatRoomId, `lt:${Date.now()}:vu${__VU}`);
      sleep(1);
    }
    return;
  }

  const { res } = chatSession(me.token, room.chatRoomId, `vu${__VU}`, {
    holdSec: HOLD, sendEverySec: 12, cursor: opened.cursor, partyId: room.partyId,
    onConnected: () => connected.add(1),
    onLatency: (ms) => latency.add(ms),
    onError: (e) => { wsErrors.add(1); console.error(`vu${__VU}: ${e}`); },
  });
  check(res, { '핸드셰이크 101': (r) => r && r.status === 101 });
}

export function teardown(data) {
  for (const g of Object.keys(data.rooms)) finishRoom(groupUsers(users, Number(g))[0].token, data.rooms[g].partyId);
}
