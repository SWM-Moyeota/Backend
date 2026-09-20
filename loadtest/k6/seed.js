// 부하테스트 계정 시딩. 승객 PASSENGERS 명 + 기사 DRIVERS 명을 API 로 만든다. 이미 있으면(409) 건너뛰므로 여러 번 실행해도 된다.
//   k6 run -e PASSENGERS=1000 -e DRIVERS=400 seed.js
// 기사 = 유저 가입 → 로그인 → 기사 등록 → 셀프 승인 → 콜 수신 켜기. 온라인(위치 등록)은 시나리오가 직접 한다.
import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { BASE_URL, PASSWORD, PASSENGER_POOL, DRIVER_POOL, passengerLoginId, driverLoginId } from './lib/config.js';

export const options = {
  scenarios: {
    seed: { executor: 'shared-iterations', vus: 20, iterations: PASSENGER_POOL + DRIVER_POOL, maxDuration: '30m' },
  },
  thresholds: { checks: ['rate>0.99'] },
};

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

function registerUser(loginId, nickname, phone) {
  const res = http.post(`${BASE_URL}/api/v1/auth/register`, JSON.stringify({
    loginId, password: PASSWORD, nickname, name: '부하테스트',
    birthDate: '1995-01-01T00:00:00Z', phoneNumber: phone, gender: 'MALE',
    email: `${loginId}@loadtest.local`,
  }), { ...JSON_HEADERS, tags: { name: 'seed_register' } });
  return check(res, { '가입 201/409': (r) => r.status === 201 || r.status === 409 })
    || fail(`가입 실패 ${loginId} ${res.status} ${res.body}`);
}

function login(loginId) {
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ loginId, password: PASSWORD }),
    { ...JSON_HEADERS, tags: { name: 'seed_login' } });
  if (res.status !== 200) fail(`로그인 실패 ${loginId} ${res.status} ${res.body}`);
  return res.json('accessToken');
}

export default function () {
  const i = exec.scenario.iterationInTest;

  if (i < PASSENGER_POOL) {
    registerUser(passengerLoginId(i), `p${String(i).padStart(6, '0')}`, `0101${String(i).padStart(7, '0')}`);
    return;
  }

  const d = i - PASSENGER_POOL;
  const loginId = driverLoginId(d);
  registerUser(loginId, `d${String(d).padStart(6, '0')}`, `0102${String(d).padStart(7, '0')}`);

  const token = login(loginId);
  const auth = { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };

  const reg = http.post(`${BASE_URL}/api/v1/drivers`, JSON.stringify({
    qualificationNumber: `서울-${String(d).padStart(4, '0')}-0000`,
    bankName: '국민은행', accountNumber: `${String(d).padStart(6, '0')}-01-000000`,
    vehicle: { seats: 4, plateNumber: `${String(d % 100).padStart(2, '0')}가${String(1000 + d).slice(-4)}`, type: '중형' },
  }), { ...auth, tags: { name: 'seed_driver_register' } });
  check(reg, { '기사 등록 200/409': (r) => r.status === 200 || r.status === 409 });

  const verify = http.post(`${BASE_URL}/api/v1/drivers/verify`, null, { ...auth, tags: { name: 'seed_driver_verify' } });
  check(verify, { '기사 승인 204/409': (r) => r.status === 204 || r.status === 409 });

  const call = http.post(`${BASE_URL}/api/v1/drivers/call`, null, { ...auth, tags: { name: 'seed_driver_call' } });
  check(call, { '콜 수신 켜기 204': (r) => r.status === 204 });
}
