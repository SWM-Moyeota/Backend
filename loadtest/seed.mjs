// 사용자 N명 가입·로그인 → users.json. k6 시나리오들이 이 파일을 읽는다.
//   BASE_URL=http://TARGET:8080 USERS=350 node loadtest/seed.mjs
// 토큰 유효 30분 - 지나면 다시 실행 (이미 가입된 번호는 문자 요청이 409 라 가입을 건너뛰고 로그인만 한다)
// 가입 전에 문자 인증을 거친다. dev 는 인증번호가 고정(000000)이라 그대로 입력한다
import { writeFileSync } from 'node:fs';

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const N = Number(process.env.USERS || 350);
const RUN = (process.env.RUN_ID || String(Date.now())).slice(-4);   // 아이디 충돌 방지 접두사
const SMS_CODE = process.env.SMS_CODE || '000000';                   // dev 고정 인증번호

async function post(path, body) {
  const res = await fetch(`${BASE}${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  return { status: res.status, body: await res.json().catch(() => ({})) };
}

// 문자 인증 → 가입용 토큰. 이미 가입된 번호면 null (재실행 시 가입 건너뜀)
async function verify(phoneNumber) {
  const send = await post('/api/v1/auth/sms/send', { phoneNumber });
  if (send.status === 409) return null;
  if (send.status !== 204) throw new Error(`인증번호 요청 실패 ${phoneNumber}: ${send.status} ${JSON.stringify(send.body)}`);
  const res = await post('/api/v1/auth/sms/verify', { phoneNumber, code: SMS_CODE });
  if (res.status !== 200) throw new Error(`인증 실패 ${phoneNumber}: ${res.status} ${JSON.stringify(res.body)}`);
  return res.body.verificationToken;
}

async function user(i) {
  const loginId = `lt${RUN}u${i}`;                                    // ^[a-z][a-z0-9_]{3,19}$
  const password = 'Passw0rd!';
  const phoneNumber = `010${RUN}${String(i).padStart(4, '0')}`;      // 11자리, unique
  const verificationToken = await verify(phoneNumber);
  if (verificationToken) {
    const reg = await post('/api/v1/auth/register', {
      loginId, password,
      nickname: `lt${RUN}${String(i).padStart(3, '0')}`,             // 2~10자 영숫자, unique
      name: '부하테스트',
      phoneNumber,
      email: `${loginId}@loadtest.local`,
      birthDate: '2000-01-01T00:00:00Z', gender: i % 2 === 0 ? 'MALE' : 'FEMALE',   // develop 부터 필수
      verificationToken,
    });
    if (reg.status !== 200 && reg.status !== 201 && reg.status !== 409) throw new Error(`가입 실패 ${i}: ${reg.status} ${JSON.stringify(reg.body)}`);
  }
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
