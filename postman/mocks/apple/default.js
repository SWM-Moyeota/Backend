// 로컬 전용. 키는 실행할 때 메모리에서 생성하고 저장하거나 출력하지 않는다.
const http = require('node:http');
const crypto = require('node:crypto');
const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const key = { ...publicKey.export({ format: 'jwk' }), kid: 'local-apple-test', alg: 'RS256', use: 'sig' };
const codes = new Map();
const refreshTokens = new Set();
let mode = 'healthy';
const counts = { keys: 0, exchange: 0, revoke: 0 };
const b64 = value => Buffer.from(JSON.stringify(value)).toString('base64url');
function token(claims) {
  const input = `${b64({ alg: 'RS256', kid: key.kid })}.${b64(claims)}`;
  return `${input}.${crypto.sign('RSA-SHA256', Buffer.from(input), privateKey).toString('base64url')}`;
}
function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(body == null ? '' : JSON.stringify(body));
}
async function body(req) {
  let value = req.body;
  if (value === undefined) {
    const chunks = [];
    for await (const part of req) chunks.push(part);
    value = Buffer.concat(chunks).toString();
  }
  if (Buffer.isBuffer(value)) value = value.toString();
  if (typeof value === 'object' && value !== null) return value;
  if (!value) return {};
  if ((req.headers['content-type'] || '').includes('application/json')) return JSON.parse(value);
  return Object.fromEntries(new URLSearchParams(value));
}
http.createServer(async (req, res) => {
  try {
    const path = new URL(req.url, 'http://localhost').pathname;
    const input = await body(req);
    // 이 두 경로는 테스트 제어용이며 Apple 실제 API에 존재하지 않는다.
    if (req.method === 'POST' && path === '/__test/mode') {
      if (!['healthy', 'unavailable', 'invalid-grant', 'revoke-unavailable'].includes(input.mode)) {
        return send(res, 400, { error: 'invalid_test_mode' });
      }
      mode = input.mode;
      return send(res, 200, { mode });
    }
    if (req.method === 'GET' && path === '/__test/stats') return send(res, 200, { ...counts, mode, activeRefreshTokens: refreshTokens.size });
    if (req.method === 'POST' && path === '/__test/identity') {
      if (!input.nonce) return send(res, 400, { error: 'nonce_required' });
      const now = Math.floor(Date.now() / 1000);
      const claims = { iss: 'https://appleid.apple.com', aud: input.audience || 'com.moyeota', sub: input.subject || 'local-apple-user', iat: now, exp: now + 300, nonce: input.nonce };
      if (input.email !== null) Object.assign(claims, { email: input.email || 'test@example.invalid', email_verified: 'true' });
      const identityToken = token(claims);
      const authorizationCode = crypto.randomUUID();
      codes.set(authorizationCode, { identityToken, audience: claims.aud });
      return send(res, 200, { identityToken, authorizationCode });
    }
    if (mode === 'unavailable') return send(res, 503, { error: 'temporarily_unavailable' });
    if (req.method === 'GET' && path === '/auth/keys') {
      counts.keys++;
      return send(res, 200, { keys: [key] });
    }
    if (req.method === 'POST' && path === '/auth/token') {
      counts.exchange++;
      if (!input.client_id || !input.client_secret) return send(res, 400, { error: 'invalid_client' });
      const entry = codes.get(input.code);
      if (mode === 'invalid-grant' || input.grant_type !== 'authorization_code' || !entry || input.client_id !== entry.audience) return send(res, 400, { error: 'invalid_grant' });
      codes.delete(input.code);
      const refresh = crypto.randomUUID();
      refreshTokens.add(refresh);
      return send(res, 200, { access_token: crypto.randomUUID(), token_type: 'Bearer', expires_in: 3600, refresh_token: refresh, id_token: entry.identityToken });
    }
    if (req.method === 'POST' && path === '/auth/revoke') {
      counts.revoke++;
      if (mode === 'revoke-unavailable') return send(res, 503, { error: 'temporarily_unavailable' });
      if (!input.client_id || !input.client_secret || !input.token) return send(res, 400, { error: 'invalid_request' });
      refreshTokens.delete(input.token);
      return send(res, 200);
    }
    return send(res, 501, { error: 'not_modelled' });
  } catch (_) { return send(res, 400, { error: 'invalid_request' }); }
}).listen(process.env.PORT || 4500, '127.0.0.1');
