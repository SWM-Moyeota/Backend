// 시나리오 3. 승객 호출과 배차 경쟁 - 같은 출발지에 기사 K 명이 대기, 정원 충족 시 K 명이 동시에 수락을 시도
// 정확히 한 명만 204, 나머지는 409(CALL_CLOSED) 여야 한다. 앱은 TAXI_ENABLED=true.
//   k6 run -e PROFILE=load -e RACE_DRIVERS=5 scenarios/dispatch-race.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { rampingVus, maxVus, BASE_THRESHOLDS, testId, BASE_URL } from '../lib/config.js';
import { loginPassengers, loginDrivers, slice } from '../lib/auth.js';
import { dispatch, openAndFill, BOARD_STATUSES } from '../lib/api.js';
import { partyRequest, jitter } from '../lib/geo.js';
import { waitForCall } from '../lib/journey.js';

const CAP = Number(__ENV.PARTY_CAPACITY || 2);
const K = Number(__ENV.RACE_DRIVERS || 5);
const TARGET_VUS = 20;   // load 20 VU × 5 기사 = 100명, stress ×4 = 400명 (기본 시딩 한도)

export const options = {
  scenarios: { race: rampingVus(TARGET_VUS) },
  thresholds: {
    ...BASE_THRESHOLDS,
    dispatch_notified: ['rate>0.99'],
    dispatch_single_winner: ['rate>0.999'],   // 중복 배정이 한 번이라도 나오면 정합성 버그
    'http_req_duration{name:dispatch_accept}': ['p(95)<300'],
  },
  tags: { testid: testId('dispatch-race'), scenario_name: 'dispatch-race' },
};

const winners = new Counter('dispatch_accept_winners');
const conflicts = new Counter('dispatch_accept_conflicts');
const singleWinner = new Rate('dispatch_single_winner');

export function setup() {
  const vus = maxVus(TARGET_VUS);
  return { passengers: loginPassengers(vus * CAP), drivers: loginDrivers(vus * K) };
}

export default function (data) {
  const passengers = slice(data.passengers, __VU, CAP);
  const drivers = slice(data.drivers, __VU, K);
  const body = partyRequest(CAP);
  const departure = { lat: body.departureLat, lng: body.departureLng };

  // K 명 모두 출발지 100m 안에서 대기
  const onlineRes = http.batch(drivers.map((t) => ({
    method: 'POST', url: `${BASE_URL}/api/v1/dispatch/online`,
    body: JSON.stringify({ latitude: jitter(departure, 100).lat, longitude: jitter(departure, 100).lng }),
    params: { headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' }, tags: { name: 'dispatch_online' } },
  })));
  if (!check(onlineRes[0], { '기사 전원 온라인 204': () => onlineRes.every((r) => r.status === 204) })) { sleep(1); return; }

  const partyId = openAndFill(passengers, body, check);
  if (!partyId) { sleep(1); return; }
  const filledAt = Date.now();

  // 콜이 열렸는지는 한 명만 확인하면 된다 - 같은 attempt 에서 K 명이 함께 후보로 등록된다
  if (waitForCall(drivers[0], partyId, filledAt) < 0) { sleep(1); return; }

  const results = http.batch(drivers.map((t) => dispatch.acceptRequest(t, partyId)));
  const won = results.filter((r) => r.status === 204).length;
  const lost = results.filter((r) => r.status === 409).length;
  winners.add(won);
  conflicts.add(lost);
  singleWinner.add(won === 1);
  check(results[0], {
    '수락 성공 정확히 1명': () => won === 1,
    '나머지는 409 CALL_CLOSED': () => lost === K - won,
  });

  // 승자가 운행을 끝내 승객·기사를 다음 반복에 쓸 수 있게 한다.
  // 승자가 여럿이면(정합성 버그) 실제로 배정된 기사는 마지막 쓰기 한 명뿐이므로 204 를 받은 기사를 차례로 시도한다.
  // 그래야 방이 DRIVER_ASSIGNED 에 갇혀 다음 반복의 개설이 409 로 오염되는 걸 막는다.
  const acceptedIdx = results.map((r, i) => (r.status === 204 ? i : -1)).filter((i) => i >= 0);
  for (const i of acceptedIdx) {
    const board = dispatch.board(drivers[i], partyId, { responseCallback: BOARD_STATUSES });
    if (board.status !== 204) continue;
    check(board, { '탑승 204': (r) => r.status === 204 });
    check(dispatch.complete(drivers[i], partyId, 12000), { '운행 종료 204': (r) => r.status === 204 });
    break;
  }
  sleep(Math.random() + 0.5);
}
