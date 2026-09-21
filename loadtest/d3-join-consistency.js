// D3 - 참여 경합 + 이벤트 정합성. 부하가 아니라 "맞게 동작하는가".
//   방 ROOMS 개에 각각 RACERS 명이 동시에 참여 → 방마다 정확히 2명만 성공해야 하고(비관적 락),
//   비동기로 처리되는 채팅방 입장·퇴장이 방 멤버와 끝까지 일치해야 한다(이벤트 유실·순서 역전 검출).
//   필요 사용자: ROOMS + ROOMS*RACERS (기본 10 + 80 = 90명)
//   k6 run loadtest/d3-join-consistency.js
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { openRoom, joinRoom, leaveRoom, roomDetail, finishRoom, resolveChatRoomId, chatMembers, 판교역 } from './lib/api.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const ROOMS = Number(__ENV.ROOMS || 10);
const RACERS = Number(__ENV.RACERS || 8);
const joined = new Counter('join_success');
const full = new Counter('join_full');
const other = new Counter('join_other');

export const options = {
  scenarios: { race: { executor: 'per-vu-iterations', vus: ROOMS * RACERS, iterations: 1, maxDuration: '1m' } },
  thresholds: {
    join_success: [`count==${ROOMS * 2}`],                  // 방마다 정원 3 - 방장 1 = 2
    join_full: [`count==${ROOMS * (RACERS - 2)}`],
    join_other: ['count==0'],                                // 5xx·데드락·락 타임아웃
    checks: ['rate==1'],
  },
};

const racer = (room, k) => users[ROOMS + room * RACERS + k];
const activeIds = (res) => res.json().filter((m) => m.active).map((m) => m.publicId).sort().join(',');

export function setup() {
  const rooms = [];
  for (let i = 0; i < ROOMS; i++) {
    const r = openRoom(users[i].token, 3, 판교역, `LT-race-${i}`);
    if (!r.id) throw new Error(`방 ${i} 생성 실패: ${r.body}`);
    rooms.push(r.id);
  }
  return { rooms };
}

export default function (data) {
  const room = (__VU - 1) % ROOMS;
  const k = Math.floor((__VU - 1) / ROOMS);
  const res = joinRoom(racer(room, k).token, data.rooms[room]);
  if (res.status === 200) joined.add(1);
  else if (res.status === 409 && res.body.includes('PARTY_FULL')) full.add(1);
  else { other.add(1); console.error(`방 ${room}: 예상 밖 응답 ${res.status} ${res.body}`); }
}

export function teardown(data) {
  sleep(5);                                                  // 비동기 리스너가 채팅방 입장을 끝낼 시간
  for (let i = 0; i < ROOMS; i++) {
    const host = users[i];
    const partyId = data.rooms[i];
    const detail = roomDetail(host.token, partyId);
    check(detail, { '방 멤버는 정확히 3명': (r) => r.json('members').length === 3, 'COMPLETED': (r) => r.json('status') === 'COMPLETED' });
    const partyIds = detail.json('members').map((m) => m.publicId).sort().join(',');

    const chatRoomId = resolveChatRoomId(host.token, sleep, 5);
    if (!check(chatRoomId, { '채팅방이 만들어졌다': (id) => id !== null })) continue;
    check(chatMembers(host.token, chatRoomId), { '채팅방 멤버 = 방 멤버': (r) => r.status === 200 && activeIds(r) === partyIds });

    check(finishRoom(host.token, partyId), { '종료 204': (r) => r.status === 204 });
  }

  // 참여 직후 나가기 - Joined / Left 이벤트가 뒤집히면 "방에는 없는데 채팅방에는 있는" 사람이 남는다
  const host = users[0], guest = racer(0, 0);
  const r = openRoom(host.token, 3, 판교역, 'LT-flap');
  if (r.id) {
    for (let n = 0; n < 5; n++) { joinRoom(guest.token, r.id); leaveRoom(guest.token, r.id); }
    sleep(5);
    const chatRoomId = resolveChatRoomId(host.token, sleep, 5);
    const left = roomDetail(host.token, r.id).json('members').map((m) => m.publicId).sort().join(',');
    if (chatRoomId) check(chatMembers(host.token, chatRoomId), { '들락날락한 사람이 채팅방에 남지 않는다': (x) => activeIds(x) === left });
    leaveRoom(host.token, r.id);
  }
  console.log('끝난 뒤 DB 에서 확인: SELECT count(*) FROM event_publication;  → 0 이어야 한다 (남아 있으면 리스너 실패)');
}
