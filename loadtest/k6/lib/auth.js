import http from 'k6/http';
import { fail } from 'k6';
import { BASE_URL, PASSWORD, passengerLoginId, driverLoginId, PASSENGER_POOL, DRIVER_POOL } from './config.js';

const LOGIN_BATCH = 50;

/** loginId 배열을 병렬 로그인해 accessToken 배열을 돌려준다 (setup 전용 - bcrypt 라 서버 CPU 를 꽤 쓴다) */
function loginAll(loginIds) {
  const tokens = [];
  for (let i = 0; i < loginIds.length; i += LOGIN_BATCH) {
    const chunk = loginIds.slice(i, i + LOGIN_BATCH);
    const responses = http.batch(chunk.map((loginId) => ({
      method: 'POST',
      url: `${BASE_URL}/api/v1/auth/login`,
      body: JSON.stringify({ loginId, password: PASSWORD }),
      params: { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup_login' } },
    })));
    responses.forEach((res, idx) => {
      if (res.status !== 200) fail(`로그인 실패 ${chunk[idx]} status=${res.status} body=${res.body} - seed.js 를 먼저 실행했는지 확인`);
      tokens.push(res.json('accessToken'));
    });
  }
  return tokens;
}

/** 승객 pool 앞에서부터 count 명 로그인 */
export function loginPassengers(count, offset = 0) {
  if (offset + count > PASSENGER_POOL) fail(`승객 ${offset + count}명 필요, 시딩된 수 ${PASSENGER_POOL}. PASSENGERS 를 늘려 seed.js 재실행`);
  const ids = [];
  for (let i = offset; i < offset + count; i++) ids.push(passengerLoginId(i));
  return loginAll(ids);
}

/** 기사 pool 앞에서부터 count 명 로그인 */
export function loginDrivers(count, offset = 0) {
  if (offset + count > DRIVER_POOL) fail(`기사 ${offset + count}명 필요, 시딩된 수 ${DRIVER_POOL}. DRIVERS 를 늘려 seed.js 재실행`);
  const ids = [];
  for (let i = offset; i < offset + count; i++) ids.push(driverLoginId(i));
  return loginAll(ids);
}

/** VU 번호(1부터)에 따라 pool 에서 겹치지 않는 size 개를 잘라 준다 */
export function slice(pool, vu, size) {
  const start = (vu - 1) * size;
  if (start + size > pool.length) fail(`VU ${vu} 가 쓸 계정이 부족 (pool=${pool.length}, size=${size})`);
  return pool.slice(start, start + size);
}

export function authHeaders(token, name) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, tags: { name } };
}
