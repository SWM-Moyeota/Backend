// 시나리오 2. 정상 운행 종단간 - 기사 온라인 → 파티 개설·참가 → 배차 콜 → 수락 → 도착 → 탑승 → 운행 종료
// 앱은 TAXI_ENABLED=true (기본) 로 띄운다.
//   k6 run -e PROFILE=load scenarios/ride-e2e.js
import { sleep } from 'k6';
import { rampingVus, maxVus, BASE_THRESHOLDS, testId } from '../lib/config.js';
import { loginPassengers, loginDrivers, slice } from '../lib/auth.js';
import { rideJourney } from '../lib/journey.js';

const CAP = Number(__ENV.PARTY_CAPACITY || 2);
const TARGET_VUS = 50;   // load 단계 기준 동시 여정 수

export const options = {
  scenarios: { ride: rampingVus(TARGET_VUS) },
  thresholds: {
    ...BASE_THRESHOLDS,
    dispatch_notify_ms: ['p(95)<3000'],
    dispatch_notified: ['rate>0.99'],
    ride_journey_ok: ['rate>0.99'],
    'http_req_duration{name:dispatch_accept}': ['p(95)<300'],
    'http_req_duration{name:ride_driver_location}': ['p(95)<100'],
  },
  tags: { testid: testId('ride-e2e'), scenario_name: 'ride-e2e' },
};

export function setup() {
  const vus = maxVus(TARGET_VUS);
  return { passengers: loginPassengers(vus * CAP), drivers: loginDrivers(vus) };
}

export default function (data) {
  const passengers = slice(data.passengers, __VU, CAP);
  const [driver] = slice(data.drivers, __VU, 1);
  rideJourney(passengers, driver);
  sleep(Math.random() * 2 + 1);
}
