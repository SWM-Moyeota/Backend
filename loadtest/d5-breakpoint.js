// D5 - 한계 찾기. D1 의 읽기 비율에 쓰기 10%(채팅 메시지 전송)를 섞어 10 → 200 RPS 로 올린다. 통과 기준 없음 - 꺾이는 지점을 본다.
//   꺾일 때 hikaricp_connections_pending 이 오르면 DB(Neon), system_cpu_usage 가 오르면 앱이 병목.
//   k6 run -o experimental-prometheus-rw loadtest/d5-breakpoint.js
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { listRooms, roomDetail, myChatRooms, chatMessagesAfter, sendChatRest, fillRoom, groupUsers, finishRoom, resolveChatRoomId } from './lib/api.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const GROUPS = Number(__ENV.GROUPS || 20);
const PEAK = Number(__ENV.PEAK || 200);

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate', startRate: 10, timeUnit: '1s', preAllocatedVUs: 100, maxVUs: 600,
      stages: [
        { target: 10, duration: '1m' }, { target: PEAK / 4, duration: '2m' }, { target: PEAK / 2, duration: '2m' },
        { target: PEAK, duration: '2m' }, { target: PEAK, duration: '1m' }, { target: 0, duration: '30s' },
      ],
    },
  },
  thresholds: { dropped_iterations: ['count==0'] },          // 생기면 k6 가 못 따라간 것 - 서버 한계가 아니다
};

export function setup() {
  const groups = [];
  for (let g = 0; g < GROUPS; g++) {
    const partyId = fillRoom(users, g);
    if (partyId) groups.push({ g, partyId, chatRoomId: resolveChatRoomId(groupUsers(users, g)[0].token, sleep, 5) });
  }
  return { groups };
}

export default function (data) {
  const grp = data.groups[Math.floor(Math.random() * data.groups.length)];
  const me = users[grp.g * 3 + Math.floor(Math.random() * 3)];
  const pick = Math.random();
  let res;
  if (pick < 0.10 && grp.chatRoomId) res = sendChatRest(me.token, grp.chatRoomId, `lt:${Date.now()}:bp`);   // 쓰기 10%
  else if (pick < 0.43) res = listRooms(me.token);
  else if (pick < 0.72) res = roomDetail(me.token, grp.partyId);
  else if (pick < 0.96 || !grp.chatRoomId) res = myChatRooms(me.token);
  else res = chatMessagesAfter(me.token, grp.chatRoomId, 0);
  check(res, { '2xx': (r) => r.status >= 200 && r.status < 300 });
}

export function teardown(data) {
  for (const grp of data.groups) finishRoom(groupUsers(users, grp.g)[0].token, grp.partyId);
}
