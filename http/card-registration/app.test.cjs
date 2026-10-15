const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync(`${__dirname}/app.js`, 'utf8');

function setup({ sdkResult = { billingKey: 'test-billing-key' }, failSave = false, query = '', attempt = null } = {}) {
  const elements = new Map();
  const calls = [];
  const storage = new Map(attempt ? [['portone-card-attempt', JSON.stringify(attempt)]] : []);
  const element = (id) => {
    if (!elements.has(id)) elements.set(id, { value: '', hidden: true, handlers: {},
      addEventListener(type, handler) { this.handlers[type] = handler; } });
    return elements.get(id);
  };
  const context = {
    document: { getElementById: element }, URLSearchParams, AbortSignal,
    crypto: { randomUUID: () => 'test-state' },
    location: { origin: 'http://127.0.0.1:8090', pathname: '/', search: query },
    history: { replaceState: () => calls.push(['scrub-url']) },
    sessionStorage: { getItem: (k) => storage.get(k), setItem: (k, v) => storage.set(k, v), removeItem: (k) => storage.delete(k) },
    window: { PortOne: { requestIssueBillingKey: async (request) => { calls.push(['sdk', request]); return sdkResult; } } },
    fetch: async (path, options) => {
      calls.push([path, options]);
      if (path.endsWith('/billing-customer')) return { ok: true, json: async () => ({ uuid: 'user-uuid', name: '테스터', fullName: '회원실명', phoneNumber: '01012345678', email: 'member@example.com' }) };
      return { ok: !failSave, status: 502, json: async () => ({ id: 100, label: '테스트 카드', maskedNumber: '****-1234', brand: 'VISA' }) };
    }
  };
  vm.runInNewContext(source, context);
  for (const id of ['token']) element(id).value = `test-${id}`;
  return { element, calls, storage,
    login: () => element('authenticate').handlers.click(),
    submit: () => element('register').handlers.submit({ preventDefault() {} }),
    retry: () => element('retry').handlers.click(),
    fixServer: () => { failSave = false; } };
}

test('SDK 발급 후 빌링키만 저장 요청에 전달하고 카드 정보를 표시한다', async () => {
  const app = setup(); await app.login(); await app.submit();
  assert.equal(app.calls.find(([name]) => name === 'sdk')[1].customer.customerId, 'user-uuid');
  assert.equal(app.calls.find(([name]) => name === 'sdk')[1].storeId, 'store-fbb356be-457b-4d16-be79-bf007aedf6d6');
  assert.equal(app.calls.find(([name]) => name === 'sdk')[1].channelKey, 'channel-key-b9cf4e55-1656-4f0b-b83c-d10fa5e27b6a');
  const customer = app.calls.find(([name]) => name === 'sdk')[1].customer;
  assert.equal(customer.fullName, '회원실명');
  assert.equal(customer.phoneNumber, '01012345678');
  assert.equal(customer.email, 'member@example.com');
  const save = app.calls.find(([name]) => name.endsWith('/methods'));
  assert.deepEqual(JSON.parse(save[1].body), { billingKey: 'test-billing-key' });
  assert.match(app.element('card').textContent, /\*\*\*\*-1234/);
  assert.equal(app.element('result').hidden, false);
  assert.equal(app.storage.size, 0);
});

test('발급 취소 시 서버 저장을 호출하지 않는다', async () => {
  const app = setup({ sdkResult: { code: 'FAILURE_TYPE_PG', message: '취소' } });
  await app.login(); await app.submit();
  assert.equal(app.calls.filter(([name]) => name.endsWith('/methods')).length, 0);
  assert.match(app.element('status').textContent, /취소/);
});

test('저장 실패 후 같은 빌링키를 재시도하며 SDK는 다시 호출하지 않는다', async () => {
  const app = setup({ failSave: true }); await app.login(); await app.submit();
  assert.equal(app.element('retry').hidden, false);
  assert.equal(app.element('start').disabled, true);
  app.fixServer(); await app.retry();
  assert.equal(app.calls.filter(([name]) => name === 'sdk').length, 1);
  assert.equal(app.calls.filter(([name]) => name.endsWith('/methods')).length, 2);
  assert.equal(app.element('result').hidden, false);
});

test('리디렉션 키를 URL에서 지우고 로그인 확인 후 저장한다', async () => {
  const app = setup({ query: '?state=test-state&billingKey=redirect-key',
    attempt: { state: 'test-state', owner: 'user-uuid', created: Date.now() } });
  assert.equal(app.calls[0][0], 'scrub-url');
  await app.login(); await app.retry();
  assert.equal(app.calls.filter(([name]) => name === 'sdk').length, 0);
  assert.equal(JSON.parse(app.calls.find(([name]) => name.endsWith('/methods'))[1].body).billingKey, 'redirect-key');
});

test('리디렉션 요청 상태가 일치하지 않으면 저장하지 않는다', async () => {
  const app = setup({ query: '?state=wrong&billingKey=untrusted-key',
    attempt: { state: 'test-state', owner: 'user-uuid', created: Date.now() } });
  await app.login(); await app.retry();
  assert.equal(app.calls.filter(([name]) => name.endsWith('/methods')).length, 0);
});
