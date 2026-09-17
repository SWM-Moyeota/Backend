// S1 - 승객 200명이 목록·방 상세·채팅방 목록을 10초 주기로 폴링 (20 RPS 고정, 5분)
//   k6 run -o experimental-prometheus-rw loadtest/s1-polling.js
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { listRooms, roomDetail, myChatRooms, openRoom, leaveRoom } from './lib/api.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const ROOMS = Number(__ENV.ROOMS || 20);   // 상세 폴링 대상 방 수 (방장 = users[0..ROOMS-1])

export const options = {
  scenarios: {
    polling: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RPS || 20), timeUnit: '1s',
      duration: __ENV.DURATION || '5m',
      preAllocatedVUs: 50, maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:GET /matching/rooms (viewport)}': ['p(95)<300'],
    'http_req_duration{name:GET /matching/rooms/{id}}': ['p(95)<300'],
    'http_req_duration{name:GET /chat-rooms/me}': ['p(95)<300'],
  },
};

export function setup() {
  const rooms = [];
  for (let i = 0; i < ROOMS; i++) {
    const r = openRoom(users[i].token, 3);
    if (r.id) rooms.push({ id: r.id, host: i });
  }
  return { rooms };
}

export default function (data) {
  const u = users[Math.floor(Math.random() * users.length)];   // 아무 승객
  const room = data.rooms[Math.floor(Math.random() * data.rooms.length)];
  const pick = Math.random();
  let res;
  if (pick < 0.4) res = listRooms(u.token);                    // 40% 지도 목록
  else if (pick < 0.8) res = roomDetail(u.token, room.id);     // 40% 방 상세 (대기 화면)
  else res = myChatRooms(u.token);                              // 20% 채팅방 목록
  check(res, { '200': (r) => r.status === 200 });
}

export function teardown(data) {
  for (const room of data.rooms) leaveRoom(users[room.host].token, room.id);   // 방장이 나가면 CANCELED
}
