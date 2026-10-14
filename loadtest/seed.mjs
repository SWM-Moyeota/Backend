// 사용자 N명 가입·로그인 → users.json. k6 시나리오들이 이 파일을 읽는다.
//   BASE_URL=http://TARGET:8080 USERS=350 node loadtest/seed.mjs
// 속도 조절 (안 주면 10명씩 동시에 최대한 빠르게)
//   PARALLEL=30                동시에 진행할 최대 인원
//   RATE=2                     초당 출발 인원
//   DURATION_SEC=300           전체를 이 시간에 맞춘다 (RATE 보다 우선)
//   예) USERS=4000 DURATION_SEC=300 PARALLEL=40 → 4000명을 약 5분에
// 토큰 유효 30분 - 지나면 다시 실행 (이미 가입된 번호는 문자 요청이 409 라 가입을 건너뛰고 로그인만 한다)
// 가입 전에 문자 인증을 거친다. dev 는 인증번호가 고정(000000)이라 그대로 입력한다
import { writeFileSync } from 'node:fs';

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const N = Number(process.env.USERS || 350);
const RUN = (process.env.RUN_ID || String(Date.now())).slice(-4);   // 아이디 충돌 방지 접두사
const SMS_CODE = process.env.SMS_CODE || '000000';                   // dev 고정 인증번호
const PARALLEL = Number(process.env.PARALLEL || 10);                 // bcrypt 비용이 있어 기본 10
const DURATION_SEC = Number(process.env.DURATION_SEC || 0);
const RATE = DURATION_SEC > 0 ? N / DURATION_SEC : Number(process.env.RATE || 0);   // 0 이면 속도 제한 없음
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

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
const failures = [];
const started = Date.now();
const inflight = new Set();

// 한 명이 실패해도 멈추지 않는다 - 성공한 사람만 저장하고 실패는 끝에 보여준다
async function run(i) {
  try {
    users.push(await user(i));
  } catch (e) {
    failures.push({ i, error: e.message });
  }
  process.stdout.write(`\r${users.length}/${N} 실패 ${failures.length}`);
}

for (let i = 0; i < N; i++) {
  while (inflight.size >= PARALLEL) await Promise.race(inflight);   // 진행 중이 PARALLEL 이면 하나 끝날 때까지 대기
  const p = run(i).finally(() => inflight.delete(p));
  inflight.add(p);
  if (RATE > 0) {                                                    // i 번째는 시작 후 i/RATE 초에 출발 - 서버가 빨라도 앞당기지 않는다
    const next = started + ((i + 1) * 1000) / RATE;
    if (next > Date.now()) await sleep(next - Date.now());
  }
}
await Promise.all(inflight);

users.sort((a, b) => a.i - b.i);                                     // k6 가 users[i] 로 조를 짠다 - 끝난 순서가 아니라 번호순
writeFileSync(new URL('./users.json', import.meta.url), JSON.stringify(users));
console.log(`\nusers.json 저장 (${users.length}명, 실패 ${failures.length}, ${Math.round((Date.now() - started) / 1000)}초, RUN_ID=${RUN})`);
if (failures.length) console.log('실패 예:', failures.slice(0, 5));
