// S5 - 30명이 동시에 방 생성. 트랜잭션 안의 외부 API(네이버) 호출이 커넥션 풀(10)을 얼마나 잡는지.
// 반드시 RouteFinder 를 가짜로 바꾼 loadtest 프로필 서버에 쏠 것 - 실제 네이버 API 에 부하 금지.
// 목적지를 조금씩 바꿔서 경로 캐시(Redis)에 안 걸리게 한다.
//   k6 run -o experimental-prometheus-rw loadtest/s5-open-party.js
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { openRoom, leaveRoom, 판교역 } from './lib/api.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const N = Number(__ENV.OPENERS || 30);
const OFFSET = 150;   // S1(0..19)·S3(100..149) 와 안 겹치게

export const options = {
  scenarios: { burst: { executor: 'per-vu-iterations', vus: N, iterations: 1, maxDuration: '1m' } },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:POST /matching/rooms}': ['p(95)<2000'],   // 외부 API 지연 포함
  },
};

export default function () {
  const u = users[OFFSET + __VU - 1];
  const dest = { lat: 판교역.lat + (__VU * 0.0007), lng: 판교역.lng + (__VU * 0.0007) };   // 캐시 미스 유도
  const r = openRoom(u.token, 3, dest);
  check(r, { '생성 200': (x) => x.status === 200 });
  if (r.id) leaveRoom(u.token, r.id);   // 다음 실행을 위해 바로 정리 (ALREADY_JOINED_OTHER_PARTY 방지)
}
