// 시나리오 1. 승객 매칭 - 파티 개설 → 지도 범위 조회 → 동시 참가(정원 충족) → 상세 조회 → 기사 없이 종료
// ⚠ 앱을 TAXI_ENABLED=false 로 띄워야 한다. 택시 모듈이 켜져 있으면 정원 충족 즉시 MATCHING 으로 넘어가
//    finish 가 409(PARTY_NOT_COMPLETED) 로 막히고 승객이 3분간 방에 갇혀 다음 반복이 실패한다.
//   k6 run -e PROFILE=load -e PARTY_CAPACITY=3 scenarios/matching.js
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';
import { rampingVus, maxVus, BASE_THRESHOLDS, testId } from '../lib/config.js';
import { loginPassengers, slice } from '../lib/auth.js';
import { matching, openAndFill } from '../lib/api.js';
import { partyRequest, gangnamBbox } from '../lib/geo.js';

const CAP = Number(__ENV.PARTY_CAPACITY || 2);     // 2~4. 클수록 같은 행에 대한 동시 참가(락 경합)가 커진다
const TARGET_VUS = 100;                             // load 단계 기준 동시 파티 수

export const options = {
  scenarios: { matching: rampingVus(TARGET_VUS) },
  thresholds: {
    ...BASE_THRESHOLDS,
    'http_req_duration{name:matching_open}': ['p(95)<300'],
    'http_req_duration{name:matching_join}': ['p(95)<300'],
    'http_req_duration{name:matching_list_bbox}': ['p(95)<200'],
    matching_cycle_ms: ['p(95)<1500'],
  },
  tags: { testid: testId('matching'), scenario_name: 'matching' },
};

const cycle = new Trend('matching_cycle_ms', true);

export function setup() {
  return { passengers: loginPassengers(maxVus(TARGET_VUS) * CAP) };
}

export default function (data) {
  const tokens = slice(data.passengers, __VU, CAP);
  const start = Date.now();

  const partyId = openAndFill(tokens, partyRequest(CAP), check);
  if (!partyId) { sleep(1); return; }

  check(matching.listWithin(tokens[0], gangnamBbox()), { '지도 범위 조회 200': (r) => r.status === 200 });
  check(matching.detail(tokens[CAP - 1], partyId), { '파티 상세 200': (r) => r.status === 200 });

  const fin = matching.finish(tokens[0], partyId);
  if (fin.status === 409 && fin.json('code') === 'PARTY_NOT_COMPLETED') {
    exec.test.abort('앱이 TAXI_ENABLED=true 로 떠 있음. 매칭 시나리오는 TAXI_ENABLED=false 로 앱을 다시 띄워서 실행');
  }
  check(fin, { '기사 없이 종료 204': (r) => r.status === 204 });

  cycle.add(Date.now() - start);
  sleep(Math.random() * 2 + 1);   // 사용자 think time 1~3초
}
