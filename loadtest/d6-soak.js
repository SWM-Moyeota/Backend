// D6 - 지속. D2 를 낮은 부하로 45분 돌려 메모리·연결·CPU 가 시간이 지나며 새는지 본다.
//   서버 env 에 JWT_ACCESS_VALIDITY=2h 를 임시로 넣을 것 - 기본 30분이면 도중에 토큰이 만료된다.
//   방치된 방의 자동 정리는 여기서 보지 않는다 - 약속(또는 생성)에서 1일 뒤에 돌아 부하 테스트 시간 안에 볼 수 없다(서버 통합 테스트가 검증한다).
//   ./k6 run -o experimental-prometheus-rw -e VUS=30 loadtest/d6-soak.js
import { SharedArray } from 'k6/data';
import { journey, FINISH_RESULTS } from './lib/journey.js';
import { breakdown } from './lib/api.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const VUS = Number(__ENV.VUS || 30);

export const options = {
  scenarios: {
    soak: {
      executor: 'ramping-vus', startVUs: 3, gracefulRampDown: '7m', gracefulStop: '7m',   // 돌던 방은 출발까지 가게 둔다(한 바퀴 5분 30초)
      stages: [{ target: VUS, duration: '2m' }, { target: VUS, duration: __ENV.HOLD || '45m' }, { target: 0, duration: '1m' }],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    ws_errors: ['count==0'],
    ...breakdown('journey_finished', 'result', FINISH_RESULTS),
    'journey_finished{result:timeout}': ['count==0'],          // 확정한 방은 모두 출발(FINISHED)까지 가야 한다
  },
};

export default function () { journey(users, __VU); }
