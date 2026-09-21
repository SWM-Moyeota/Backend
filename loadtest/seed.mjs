// 사용자 N명 가입·로그인 → users.json. k6 시나리오들이 이 파일을 읽는다.
//   BASE_URL=http://TARGET:8080 USERS=350 node loadtest/seed.mjs
// 토큰 유효 30분 - 지나면 다시 실행 (가입은 409 로 건너뛰고 로그인만 한다)
import { writeFileSync } from 'node:fs';

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const N = Number(process.env.USERS || 350);
const RUN = (process.env.RUN_ID || String(Date.now())).slice(-4);   // 아이디 충돌 방지 접두사

async function post(path, body) {
  const res = await fetch(`${BASE}${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  return { status: res.status, body: await res.json().catch(() => ({})) };
}

async function user(i) {
  const loginId = `lt${RUN}u${i}`;                                    // ^[a-z][a-z0-9_]{3,19}$
  const password = 'Passw0rd!';
  const reg = await post('/api/v1/auth/register', {
    loginId, password,
    nickname: `lt${RUN}${String(i).padStart(3, '0')}`,               // 2~10자 영숫자, unique
    name: '부하테스트',
    phoneNumber: `010${RUN}${String(i).padStart(4, '0')}`,           // 11자리, unique
    email: `${loginId}@loadtest.local`,
    birthDate: '2000-01-01T00:00:00Z', gender: i % 2 === 0 ? 'MALE' : 'FEMALE',   // develop 부터 필수
  });
  if (reg.status !== 200 && reg.status !== 201 && reg.status !== 409) throw new Error(`가입 실패 ${i}: ${reg.status} ${JSON.stringify(reg.body)}`);
  const login = await post('/api/v1/auth/login', { loginId, password });
  if (login.status !== 200) throw new Error(`로그인 실패 ${i}: ${login.status} ${JSON.stringify(login.body)}`);
  return { i, loginId, token: login.body.accessToken };
}

const users = [];
for (let start = 0; start < N; start += 10) {                        // bcrypt 비용이 있어 10개씩 병렬
  const batch = await Promise.all(Array.from({ length: Math.min(10, N - start) }, (_, k) => user(start + k)));
  users.push(...batch);
  process.stdout.write(`\r${users.length}/${N}`);
}
writeFileSync(new URL('./users.json', import.meta.url), JSON.stringify(users));
console.log(`\nusers.json 저장 (${users.length}명, RUN_ID=${RUN})`);
