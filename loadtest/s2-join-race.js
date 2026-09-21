// S2 - 정원 3인 방 하나에 10명이 동시에 join. 정확히 2명만 성공하고 8명은 409 PARTY_FULL 이어야 한다.
// 부하가 아니라 정합성 검증. 한 번 돌리면 방이 차니 반복하려면 RUN 마다 새로 실행.
//   k6 run loadtest/s2-join-race.js
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { openRoom, joinRoom, leaveRoom, roomDetail } from './lib/api.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const RACERS = 10;
const joined = new Counter('join_success');
const full = new Counter('join_full');
const other = new Counter('join_other');

export const options = {
  scenarios: { race: { executor: 'per-vu-iterations', vus: RACERS, iterations: 1, maxDuration: '30s' } },
  thresholds: {
    join_success: ['count==2'],        // 정원 3 - 방장 1 = 2
    join_full: ['count==8'],
    join_other: ['count==0'],          // 5xx·데드락이면 여기 잡힌다
  },
};

export function setup() {
  const host = users[0];
  const r = openRoom(host.token, 3);
  if (!r.id) throw new Error(`방 생성 실패: ${r.body}`);
  return { partyId: r.id };
}

export default function (data) {
  const me = users[__VU];                       // VU 1..10 → users[1..10], 방장(0)과 겹치지 않음
  const res = joinRoom(me.token, data.partyId);
  if (res.status === 200) joined.add(1);
  else if (res.status === 409 && res.body.includes('PARTY_FULL')) full.add(1);
  else { other.add(1); console.error(`예상 밖 응답 ${res.status}: ${res.body}`); }
}

export function teardown(data) {
  const detail = roomDetail(users[0].token, data.partyId);
  check(detail, { '최종 인원 3명': (r) => r.json('members').length === 3 });
  // 정리: 참여자 → 방장 순으로 나가면 마지막에 방이 CANCELED. 배차가 켜진 서버면 MATCHING 이라 못 나감(409) - 그 경우 다음 실행은 새 RUN_ID 로 seed
  for (let v = 1; v <= RACERS; v++) leaveRoom(users[v].token, data.partyId);   // 비멤버는 403 - 무시
  leaveRoom(users[0].token, data.partyId);
}
