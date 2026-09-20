// 시나리오 5. 정상 서비스 혼합 - 기사 위치 보고(배경 부하) + 운행 여정 + 승객의 파티 탐색이 동시에 돈다
// 개별 시나리오에서 잡은 SLO 가 섞였을 때도 유지되는지 본다. 앱은 TAXI_ENABLED=true.
//   k6 run -e PROFILE=load scenarios/mixed.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { rampingVus, arrivalRate, maxVus, BASE_THRESHOLDS, testId, BASE_URL, PROFILE } from '../lib/config.js';
import { loginPassengers, loginDrivers, slice } from '../lib/auth.js';
import { matching, dispatch } from '../lib/api.js';
import { GANGNAM, jitter, gangnamBbox } from '../lib/geo.js';
import { rideJourney } from '../lib/journey.js';

const CAP = Number(__ENV.PARTY_CAPACITY || 2);
const RIDE_VUS = 30;                 // load 기준 동시 여정
const BROWSE_VUS = 20;               // 파티를 둘러보기만 하는 승객
const BG_DRIVERS = Number(__ENV.ONLINE_DRIVERS || (PROFILE === 'smoke' ? 10 : 100));   // 위치만 보고하는 배경 기사

export const options = {
  scenarios: {
    location: arrivalRate(BG_DRIVERS / 3, 'locationLoop'),
    ride: rampingVus(RIDE_VUS, 'rideLoop'),
    browse: rampingVus(BROWSE_VUS, 'browseLoop'),
  },
  thresholds: {
    ...BASE_THRESHOLDS,
    dispatch_notify_ms: ['p(95)<3000'],
    dispatch_notified: ['rate>0.99'],
    ride_journey_ok: ['rate>0.99'],
    'http_req_duration{name:dispatch_location}': ['p(95)<100'],
    'http_req_duration{name:matching_list_bbox}': ['p(95)<200'],
  },
  tags: { testid: testId('mixed'), scenario_name: 'mixed' },
};

// k6 의 VU 번호(__VU, exec.vu.idInTest)는 시나리오를 가리지 않고 전체에서 유일하다.
// 그래서 ride 처럼 VU 마다 전용 계정이 필요한 시나리오는 전체 VU 수만큼 pool 을 잡고 idInTest 로 자른다.
const TOTAL_VUS = options.scenarios.location.maxVUs + maxVus(RIDE_VUS) + maxVus(BROWSE_VUS);
const BROWSE_POOL = 50;   // 조회만 하므로 토큰을 여러 VU 가 나눠 써도 된다

export function setup() {
  const bgDrivers = loginDrivers(BG_DRIVERS, 0);
  const rideDrivers = loginDrivers(TOTAL_VUS, BG_DRIVERS);
  const ridePassengers = loginPassengers(TOTAL_VUS * CAP, 0);
  const browsePassengers = loginPassengers(BROWSE_POOL, TOTAL_VUS * CAP);

  const res = http.batch(bgDrivers.map((t) => ({
    method: 'POST', url: `${BASE_URL}/api/v1/dispatch/online`,
    body: JSON.stringify({ latitude: jitter(GANGNAM, 3000).lat, longitude: jitter(GANGNAM, 3000).lng }),
    params: { headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' }, tags: { name: 'setup_online' } },
  })));
  const failed = res.filter((r) => r.status !== 204).length;
  if (failed > 0) throw new Error(`배경 기사 온라인 실패 ${failed}/${BG_DRIVERS}`);

  return { bgDrivers, rideDrivers, ridePassengers, browsePassengers };
}

export function locationLoop(data) {
  const token = data.bgDrivers[exec.scenario.iterationInTest % BG_DRIVERS];
  check(dispatch.location(token, jitter(GANGNAM, 3000)), { '위치 보고 204': (r) => r.status === 204 });
}

export function rideLoop(data) {
  const vu = exec.vu.idInTest;
  const passengers = slice(data.ridePassengers, vu, CAP);
  const [driver] = slice(data.rideDrivers, vu, 1);
  rideJourney(passengers, driver);
  sleep(Math.random() * 2 + 1);
}

export function browseLoop(data) {
  const token = data.browsePassengers[exec.vu.idInTest % BROWSE_POOL];
  const list = matching.listWithin(token, gangnamBbox());
  check(list, { '지도 범위 조회 200': (r) => r.status === 200 });
  const items = list.status === 200 ? list.json('list') : [];
  if (items.length > 0) {
    const pick = items[Math.floor(Math.random() * items.length)];
    check(matching.detail(token, pick.partyId), { '파티 상세 200': (r) => r.status === 200 });
  }
  sleep(Math.random() * 3 + 2);
}
