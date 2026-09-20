// 시나리오 4. 기사 위치 보고 - 온라인 기사 N 명이 3초 간격으로 POST /dispatch/location (Redis GEOADD)
// 단독으로는 Redis 쓰기 처리량을, mixed 에서는 이 부하가 배차 탐색(GEOSEARCH)에 주는 영향을 본다.
//   k6 run -e PROFILE=load -e ONLINE_DRIVERS=200 scenarios/driver-location.js
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { arrivalRate, BASE_THRESHOLDS, testId, BASE_URL, PROFILE } from '../lib/config.js';
import { loginDrivers } from '../lib/auth.js';
import { dispatch } from '../lib/api.js';
import { GANGNAM, jitter } from '../lib/geo.js';

const REPORT_INTERVAL_S = 3;
const N = Number(__ENV.ONLINE_DRIVERS || (PROFILE === 'smoke' ? 10 : 200));

export const options = {
  scenarios: { location: arrivalRate(N / REPORT_INTERVAL_S) },
  thresholds: {
    ...BASE_THRESHOLDS,
    'http_req_duration{name:dispatch_location}': ['p(95)<100', 'p(99)<200'],
  },
  tags: { testid: testId('driver-location'), scenario_name: 'driver-location' },
};

export function setup() {
  const drivers = loginDrivers(N);
  // 전원 온라인. 강남역 반경 3km 에 흩어 둔다
  const res = http.batch(drivers.map((t) => ({
    method: 'POST', url: `${BASE_URL}/api/v1/dispatch/online`,
    body: JSON.stringify({ latitude: jitter(GANGNAM, 3000).lat, longitude: jitter(GANGNAM, 3000).lng }),
    params: { headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' }, tags: { name: 'setup_online' } },
  })));
  const failed = res.filter((r) => r.status !== 204).length;
  if (failed > 0) throw new Error(`기사 온라인 실패 ${failed}/${N} - seed.js 로 기사 승인·콜 수신이 됐는지 확인`);
  return { drivers };
}

export default function (data) {
  const token = data.drivers[exec.scenario.iterationInTest % N];
  const res = dispatch.location(token, jitter(GANGNAM, 3000));
  check(res, { '위치 보고 204': (r) => r.status === 204 });
}
