// D6 - 지속. D2 를 낮은 부하로 45분 돌리고, 조의 20% 는 방을 닫지 않고 방치해 자동 종료 스윕(30분 + 주기 10분)을 확인한다.
//   서버 env 에 JWT_ACCESS_VALIDITY=2h 를 임시로 넣을 것 - 기본 30분이면 도중에 토큰이 만료된다.
//   k6 run -o experimental-prometheus-rw -e VUS=30 loadtest/d6-soak.js
import { SharedArray } from 'k6/data';
import { journey } from './lib/journey.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const VUS = Number(__ENV.VUS || 30);
const ABANDON_EVERY = Number(__ENV.ABANDON_EVERY || 5);      // 5개 조마다 1개 조가 방치 = 20%

export const options = {
  scenarios: {
    soak: {
      executor: 'ramping-vus', startVUs: 3, gracefulRampDown: '3m', gracefulStop: '3m',
      stages: [{ target: VUS, duration: '2m' }, { target: VUS, duration: __ENV.HOLD || '45m' }, { target: 0, duration: '1m' }],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    abandon_to_finished_min: ['max<42'],                       // TTL 30분 + 스윕 주기 10분 + 여유
    ws_errors: ['count==0'],
  },
};

export default function () {
  const g = Math.floor((__VU - 1) / 3);
  journey(users, __VU, { abandon: g % ABANDON_EVERY === 0 });
}
