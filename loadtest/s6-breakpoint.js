// S6 - 한계 찾기. S1 트래픽을 10 → 200 RPS 로 올리면서 p95 가 꺾이는 지점을 본다. 통과 기준 없음.
//   k6 run -o experimental-prometheus-rw loadtest/s6-breakpoint.js
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { listRooms, roomDetail, myChatRooms, openRoom, leaveRoom } from './lib/api.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const ROOMS = 20;

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 10, timeUnit: '1s',
      preAllocatedVUs: 100, maxVUs: 500,
      stages: [
        { target: 10,  duration: '1m' },
        { target: 50,  duration: '2m' },
        { target: 100, duration: '2m' },
        { target: 200, duration: '2m' },
        { target: 0,   duration: '30s' },
      ],
    },
  },
  // 기준 대신 관찰 - dropped_iterations 가 생기면 생성기(k6)가 못 따라간 것이므로 서버 한계가 아니다
  thresholds: { dropped_iterations: ['count==0'] },
};

export function setup() {
  const rooms = [];
  for (let i = 0; i < ROOMS; i++) { const r = openRoom(users[i].token, 3); if (r.id) rooms.push({ id: r.id, host: i }); }
  return { rooms };
}

export default function (data) {
  const u = users[Math.floor(Math.random() * users.length)];
  const room = data.rooms[Math.floor(Math.random() * data.rooms.length)];
  const pick = Math.random();
  const res = pick < 0.4 ? listRooms(u.token) : pick < 0.8 ? roomDetail(u.token, room.id) : myChatRooms(u.token);
  check(res, { '200': (r) => r.status === 200 });
}

export function teardown(data) {
  for (const room of data.rooms) leaveRoom(users[room.host].token, room.id);
}
