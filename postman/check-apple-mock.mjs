import assert from 'node:assert/strict';
import { createPublicKey, verify } from 'node:crypto';

const base = process.env.APPLE_MOCK_URL || 'http://127.0.0.1:4517/apple';
async function request(path, value, form = false) {
  const response = await fetch(base + path, value === undefined ? { signal: AbortSignal.timeout(5000) } : {
    method: 'POST', signal: AbortSignal.timeout(5000),
    headers: { 'Content-Type': form ? 'application/x-www-form-urlencoded' : 'application/json' },
    body: form ? new URLSearchParams(value).toString() : JSON.stringify(value),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
}
await request('/__test/mode', { mode: 'healthy' });
const keys = await request('/auth/keys');
assert.equal(keys.status, 200);
const identity = await request('/__test/identity', { nonce: 'local-nonce', subject: 'smoke-user' });
assert.equal(identity.status, 200);
const parts = identity.body.identityToken.split('.');
const claims = JSON.parse(Buffer.from(parts[1], 'base64url'));
assert.equal(claims.iss, 'https://appleid.apple.com');
assert.equal(claims.nonce, 'local-nonce');
assert.ok(verify('RSA-SHA256', Buffer.from(parts.slice(0, 2).join('.')), createPublicKey({ key: keys.body.keys[0], format: 'jwk' }), Buffer.from(parts[2], 'base64url')));
const fields = { client_id: 'com.moyeota', client_secret: 'local-only-placeholder', code: identity.body.authorizationCode, grant_type: 'authorization_code' };
const exchanged = await request('/auth/token', fields, true);
assert.equal(exchanged.status, 200);
assert.equal(exchanged.body.id_token, identity.body.identityToken);
assert.equal((await request('/auth/token', fields, true)).body.error, 'invalid_grant');
await request('/__test/mode', { mode: 'revoke-unavailable' });
const revoke = { client_id: 'com.moyeota', client_secret: 'local-only-placeholder', token: exchanged.body.refresh_token, token_type_hint: 'refresh_token' };
assert.equal((await request('/auth/revoke', revoke, true)).status, 503);
await request('/__test/mode', { mode: 'healthy' });
assert.equal((await request('/auth/revoke', revoke, true)).status, 200);
await request('/__test/mode', { mode: 'unavailable' });
assert.equal((await request('/auth/keys')).status, 503);
await request('/__test/mode', { mode: 'healthy' });
assert.equal((await request('/not-modelled')).status, 501);
console.log('모의 서버 검증 통과: RSA/JWKS, nonce, 코드 교환·재사용 차단, 철회 503→200, 키 서버 503, 미구현 501');
console.log('이 결과는 실제 모여타 백엔드 또는 Apple 계정 인증 성공을 증명하지 않습니다.');
