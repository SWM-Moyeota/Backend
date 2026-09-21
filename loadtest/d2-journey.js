// D2 - 전체 여정(메인). VU 3개가 한 조로 방 생성 → 참여 → 대기 폴링 → 채팅(WebSocket) → 종료를 반복한다.
//   VUS 는 3의 배수. users.json 에 VUS 명 이상 있어야 한다.
//   k6 run -o experimental-prometheus-rw -e VUS=90 loadtest/d2-journey.js
import { SharedArray } from 'k6/data';
import { journey } from './lib/journey.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const VUS = Number(__ENV.VUS || 90);

export const options = {
  scenarios: {
    journey: {
      executor: 'ramping-vus', startVUs: 3, gracefulRampDown: '2m', gracefulStop: '2m',
      stages: [{ target: VUS, duration: '2m' }, { target: VUS, duration: __ENV.HOLD || '6m' }, { target: 0, duration: '1m' }],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    'journey_join{result:other}': ['count==0'],      // 409(PARTY_FULL) 말고 다른 실패는 없어야 한다
    chat_delivery_ms: ['p(95)<500'],
    ws_errors: ['count==0'],
    checks: ['rate>0.99'],
  },
};

export default function () { journey(users, __VU); }
