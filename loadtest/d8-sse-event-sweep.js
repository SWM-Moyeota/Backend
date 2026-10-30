// D8 - SSE 이벤트 발생률 한계. 연결 수를 고정하고 입퇴장을 EVENT_RATES 단계로 올려 어디서 꺾이는지 본다.
//   방 하나에 고정 구독자 2명(방장·조원 1)과 입퇴장 담당 1명(조원 2). 구독자는 앱처럼 시험 내내 끊지 않고, 끊기면 3초 뒤 다시 붙는다.
//   표를 채우려면 CONNS 를 바꿔 여러 번 돌린다. 결과는 node d8-table.mjs results/<파일>.json... 으로 단계별 표가 된다.
//   ./k6 run -o experimental-prometheus-rw --tag testid=d8-c300-r1 --summary-export=results/d8-c300-r1.json -e CONNS=300 d8-sse-event-sweep.js
//   짧게: -e CONNS=20 -e EVENT_RATES=5,20 -e STEP_SEC=20 -e CONNECT_SEC=5 -e WARM_SEC=5
//   사용자: (CONNS/2 + PROBE_ROOMS) x 3 명. 지표와 판정은 README 의 D8 절.
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';
import { leaveRoom, joinRoom, roomDetail, openRoom, spotOf, breakdown, groupUsers } from './lib/api.js';
import { watchParty, newRoomView, follow, compareWithDetail, sseErrors, sseReconnects, RESYNC_REASONS } from './lib/sse.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const CONNS = Number(__ENV.CONNS || 300);
const RATES = (__ENV.EVENT_RATES || '5,20,50,100,200').split(',').map(Number);
const STEP_SEC = Number(__ENV.STEP_SEC || 120);
const STEP_RAMP_SEC = Number(__ENV.STEP_RAMP_SEC || 10);
const CONNECT_SEC = Number(__ENV.CONNECT_SEC || 60);
const WARM_SEC = Number(__ENV.WARM_SEC || 30);
const SETTLE_SEC = Number(__ENV.SETTLE_SEC || 45);
const PROBE_ROOMS = Number(__ENV.PROBE_ROOMS || 5);
const PROBE_PERIOD_SEC = Number(__ENV.PROBE_PERIOD_SEC || 15);
const MAX_CHURN_VUS = Number(__ENV.MAX_CHURN_VUS || 500);
const CHURN_ROOMS = __ENV.CHURN_ROOMS ? Number(__ENV.CHURN_ROOMS) : null;
const PING_SEC = 30;

const ROOMS = CONNS / 2;
const NEEDED = (ROOMS + PROBE_ROOMS) * 3;
if (!Number.isInteger(ROOMS) || ROOMS < 1) throw new Error(`CONNS=${CONNS} 는 2 이상의 짝수여야 한다 - 방마다 고정 구독자가 2명이다`);
if (RATES.some((r) => !(r > 0))) throw new Error(`EVENT_RATES=${__ENV.EVENT_RATES} 는 양수 목록이어야 한다`);
if (users.length < NEEDED) throw new Error(`users.json 에 ${users.length}명뿐이다 - ${NEEDED}명 필요`);

const CHURN_START = CONNECT_SEC + WARM_SEC;
const CHURN_SEC = RATES.length * (STEP_RAMP_SEC + STEP_SEC);
const CHURN_END = CHURN_START + CHURN_SEC;
const CHECK_AT = CHURN_END + SETTLE_SEC;
const SUBS_SEC = CHECK_AT + PING_SEC + 15;
const STEPS = RATES.map(String);
const PHASES = ['warm', ...STEPS, 'settle'];

const actions = new Counter('d8_action');                     // kind: leave / join, result: ok / skipped / other, step
const arrivalGaps = new Counter('sse_arrival_gap');           // step - 직전에 받은 version 보다 2 이상 건너뛰어 도착
const inversions = new Counter('sse_order_inversion');        // step - 직전에 받은 version 이하로 도착
const beforeConnected = new Counter('sse_before_connected');  // connected 전에 도착한 변경분
const deliveryExpected = new Counter('sse_delivery_expected');
const deliveryReceived = new Counter('sse_delivery_received');
const deliveryLost = new Counter('sse_delivery_lost');        // step - 끊기지 않은 구독에서 뒤 번호는 받았는데 끝내 못 받은 version
const deliveryTail = new Counter('sse_delivery_tail');        // 마지막으로 받은 version 뒤의 끝 구간 - 유실이거나 닫은 뒤 커밋된 것
const unverified = new Counter('sse_delivery_unverified');    // 도중에 끊겨 받은 수를 대조하지 못한 구독 구간
const eventLatency = new Trend('sse_event_latency_ms', true); // name, step - 측정 방의 예약 시각 → 다른 구독자 수신
const tickSkips = new Counter('sse_probe_tick_skip');         // step - 측정 방에서 예약 주기 하나가 비었다. 수신 유실이거나 요청이 주기를 넘겼다
const actionMs = new Trend('d8_action_ms', true);              // step - 입퇴장 요청 자체의 응답 시간
const probeSendLate = new Trend('sse_probe_send_late_ms', true); // step - 예약 시각보다 늦게 보낸 정도. 크면 k6 가 밀렸다는 뜻

export const options = {
  setupTimeout: `${Math.ceil(NEEDED * 0.2) + 120}s`,
  teardownTimeout: `${Math.ceil(NEEDED * 0.2) + 120}s`,
  scenarios: {
    subscribers: { executor: 'constant-vus', exec: 'subscriber', vus: CONNS, duration: `${SUBS_SEC}s`, gracefulStop: '30s' },
    ...(PROBE_ROOMS > 0 ? {
      probeSubscribers: { executor: 'constant-vus', exec: 'probeSubscriber', vus: PROBE_ROOMS * 2, duration: `${SUBS_SEC}s`, gracefulStop: '30s' },
      probers: { executor: 'constant-vus', exec: 'prober', vus: PROBE_ROOMS, duration: `${CHURN_END}s`, startTime: '0s', gracefulStop: `${PROBE_PERIOD_SEC + 10}s` },
    } : {}),
    churn: {
      executor: 'ramping-arrival-rate', exec: 'churner', startRate: 0, timeUnit: '1s', startTime: `${CHURN_START}s`,
      preAllocatedVUs: Math.min(50, MAX_CHURN_VUS), maxVUs: MAX_CHURN_VUS, gracefulStop: '30s',
      stages: RATES.flatMap((r) => [{ target: r, duration: `${STEP_RAMP_SEC}s` }, { target: r, duration: `${STEP_SEC}s` }]),
    },
  },
  thresholds: {
    ...breakdown('sse_arrival_gap', 'step', PHASES),
    ...breakdown('sse_order_inversion', 'step', PHASES),
    ...breakdown('sse_delivery_lost', 'step', PHASES),
    ...breakdown('sse_probe_tick_skip', 'step', PHASES),
    ...breakdown('sse_resync', 'reason', RESYNC_REASONS),
    ...STEPS.reduce((o, s) => ({
      ...o,
      [`d8_action{step:${s},result:ok}`]: ['count>=0'],
      [`d8_action{step:${s},result:skipped}`]: ['count>=0'],
      [`d8_action{step:${s},result:other}`]: ['count>=0'],
      [`d8_action_ms{step:${s}}`]: ['p(95)>=0'],
      [`sse_probe_send_late_ms{step:${s}}`]: ['p(95)>=0'],
      [`sse_event_latency_ms{step:${s}}`]: ['p(95)<1000'],
    }), {}),
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
    sse_errors: ['count==0'],
    sse_delivery_lost: ['count==0'],
    sse_delivery_tail: ['count==0'],
    sse_delivery_unverified: ['count==0'],
    sse_probe_tick_skip: ['count==0'],
    'd8_action{result:other}': ['count==0'],
    'sse_view_check{result:match}': [`count>=${CONNS + PROBE_ROOMS * 2}`],
    'sse_view_check{result:members}': ['count==0'],
    'sse_view_check{result:status}': ['count==0'],
    'sse_view_check{result:host}': ['count==0'],
    'sse_view_check{result:version}': ['count==0'],
  },
};

function prepareRoom(g, name, withRunner) {
  const [host, a, b] = groupUsers(users, g);
  const from = spotOf(g);
  const r = openRoom(host.token, 3, from.dest, name, from);
  if (!r.id) throw new Error(`방 준비 실패 g=${g}: ${r.status} ${r.body} - sql/reset.sql 로 잔여를 지우고 새 RUN_ID 로 seed 한다`);
  for (const u of withRunner ? [a, b] : [a]) {
    const j = joinRoom(u.token, r.id);
    if (j.status !== 200) throw new Error(`참여 실패 g=${g}: ${j.status} ${j.body} - sql/reset.sql 로 잔여를 지우고 새 RUN_ID 로 seed 한다`);
  }
  return { g, partyId: r.id };
}

export function setup() {
  const rooms = [];
  for (let g = 0; g < ROOMS; g++) rooms.push(prepareRoom(g, `LT-d8-${g}`, g % 2 === 0));
  const probes = [];
  for (let p = 0; p < PROBE_ROOMS; p++) probes.push(prepareRoom(ROOMS + p, `LT-d8-probe${p}`, true));
  console.log(`방 ${rooms.length}개, 고정 연결 ${CONNS}, 단계 ${RATES.join('→')}건/s, 측정 방 ${probes.length}개(${(PROBE_ROOMS / PROBE_PERIOD_SEC).toFixed(2)}건/s 추가), 입퇴장 ${CHURN_START}~${CHURN_END}초`);
  return { rooms, probes, stepSec: STEP_SEC + STEP_RAMP_SEC, settleSec: SETTLE_SEC, probePeriodSec: PROBE_PERIOD_SEC };
}

const elapsedSec = (startedAt, base = 0) => (Date.now() - startedAt) / 1000 + base;

function stepAt(sec) {
  if (sec < CHURN_START) return 'warm';
  if (sec >= CHURN_END) return 'settle';
  return STEPS[Math.min(STEPS.length - 1, Math.floor((sec - CHURN_START) / (STEP_RAMP_SEC + STEP_SEC)))];
}

function newSegment() {
  return { first: null, lastRaw: null, stepOf: new Map() };
}

function recordArrival(seg, name, data, step) {
  if (!data || !Number.isInteger(data.version)) return;
  if (seg.first === null) beforeConnected.add(1, { name });
  if (seg.lastRaw !== null) {
    if (data.version <= seg.lastRaw) inversions.add(1, { step, name });
    else if (data.version > seg.lastRaw + 1) arrivalGaps.add(1, { step, name });
  }
  if (seg.lastRaw === null || data.version > seg.lastRaw) seg.lastRaw = data.version;
  seg.stepOf.set(data.version, step);
}

function settleDelivery(seg, finalVersion) {
  if (seg.first === null || !Number.isInteger(finalVersion)) { unverified.add(1); return; }
  let step = 'warm';
  for (let v = seg.first + 1; v <= finalVersion; v++) {
    deliveryExpected.add(1);
    if (seg.stepOf.has(v)) {
      step = seg.stepOf.get(v);
      deliveryReceived.add(1);
    } else if (seg.lastRaw === null || v > seg.lastRaw) {
      deliveryTail.add(1);
    } else {
      deliveryLost.add(1, { step });
      console.warn(`SSE 미수신 version=${v} (첫 ${seg.first}, 끝 ${finalVersion}) step~${step}`);
    }
  }
}

function hold(me, partyId, startedAt, onChange) {
  const view = newRoomView();
  while (true) {
    const remaining = CHECK_AT - elapsedSec(startedAt);
    if (remaining <= 0) return;
    const seg = newSegment();
    const opened = Date.now();
    const r = watchParty(me.token, partyId, {
      holdSec: remaining,
      onEvent: (name, data) => {
        const at = Date.now();
        if (name === 'connected') {
          if (seg.first === null && data && Number.isInteger(data.version)) seg.first = data.version;
        } else if (name === 'member-joined' || name === 'member-left') {
          recordArrival(seg, name, data, stepAt(elapsedSec(startedAt)));
          if (onChange) onChange(name, at);
        }
        follow(view, me.token, partyId, name, data);
        return true;
      },
    });
    check(r, { 'SSE 200': (x) => x.status === 200 });
    if (r.status !== 200 && r.closedBy !== 'error') sseErrors.add(1, { reason: String(r.status) });
    if (r.closedBy === 'hold' && Date.now() - opened >= remaining * 1000) {
      sleep(Math.random() * 5);
      const detail = roomDetail(me.token, partyId, 'GET /matching/rooms/{id} (check)');
      compareWithDetail(view, detail);
      if (detail.status === 200) settleDelivery(seg, detail.json('version'));
      else unverified.add(1);
      return;
    }
    unverified.add(1);
    sseReconnects.add(1);
    sleep(3);
  }
}

let slot = null;
let finished = false;

function idle(startedAt) {
  sleep(Math.max(1, SUBS_SEC - elapsedSec(startedAt)));
}

export function subscriber(data) {
  const startedAt = exec.scenario.startTime;
  if (slot === null) slot = exec.scenario.iterationInTest;
  if (finished || slot >= CONNS) { idle(startedAt); return; }
  const room = data.rooms[Math.floor(slot / 2)];
  sleep((slot / CONNS) * CONNECT_SEC);
  hold(groupUsers(users, room.g)[slot % 2], room.partyId, startedAt);
  finished = true;
  idle(startedAt);
}

const tickOffsetMs = (p) => (p * PROBE_PERIOD_SEC * 1000) / Math.max(1, PROBE_ROOMS);

export function probeSubscriber(data) {
  const startedAt = exec.scenario.startTime;
  if (slot === null) slot = exec.scenario.iterationInTest;
  if (finished || slot >= PROBE_ROOMS * 2) { idle(startedAt); return; }
  const p = Math.floor(slot / 2);
  const room = data.probes[p];
  const period = PROBE_PERIOD_SEC * 1000;
  const offset = tickOffsetMs(p);
  let lastTick = null;
  sleep((slot / (PROBE_ROOMS * 2)) * CONNECT_SEC);
  hold(groupUsers(users, room.g)[slot % 2], room.partyId, startedAt, (name, at) => {
    const tick = Math.floor((at - offset) / period);
    const step = stepAt(elapsedSec(startedAt));
    if (lastTick !== null && tick > lastTick + 1) tickSkips.add(1, { step });
    lastTick = tick;
    eventLatency.add(at - (tick * period + offset), { name, step });
  });
  finished = true;
  idle(startedAt);
}

export function prober(data) {
  const startedAt = exec.scenario.startTime;
  if (slot === null) slot = exec.scenario.iterationInTest;
  if (finished || slot >= PROBE_ROOMS) { sleep(Math.max(1, CHURN_END - elapsedSec(startedAt))); return; }
  const room = data.probes[slot];
  const who = groupUsers(users, room.g)[2];
  const period = PROBE_PERIOD_SEC * 1000;
  const offset = tickOffsetMs(slot);
  let inside = true;
  sleep(Math.max(0, CONNECT_SEC + WARM_SEC / 2 - elapsedSec(startedAt)));
  while (elapsedSec(startedAt) < CHURN_END) {
    const scheduled = (Math.floor((Date.now() - offset) / period) + 1) * period + offset;
    sleep((scheduled - Date.now()) / 1000);
    const name = inside ? 'member-left' : 'member-joined';
    const step = stepAt(elapsedSec(startedAt));
    probeSendLate.add(Date.now() - scheduled, { step });
    const res = inside ? leaveRoom(who.token, room.partyId) : joinRoom(who.token, room.partyId);
    const ok = res.status === (inside ? 204 : 200);
    actions.add(1, { kind: inside ? 'leave' : 'join', result: ok ? 'ok' : 'other', step: 'probe' });
    if (ok) inside = !inside;
    else {
      if (actionWarned++ < 5) console.error(`측정 방 ${name} ${res.status}: ${res.body}`);
      if (inside && res.status === 403 && String(res.body).includes('NOT_PARTY_MEMBER')) inside = false;
      else if (!inside && res.status === 409 && String(res.body).includes('ALREADY_JOINED_OTHER_PARTY')) inside = true;
    }
  }
  finished = true;
}

let actionWarned = 0;

export function churner(data) {
  const i = exec.scenario.iterationInTest;
  const pool = CHURN_ROOMS ? Math.min(CHURN_ROOMS, data.rooms.length) : data.rooms.length;
  const room = data.rooms[i % pool];
  const leaving = (Math.floor(i / pool) + (i % pool)) % 2 === 0;
  const who = groupUsers(users, room.g)[2];
  const res = leaving ? leaveRoom(who.token, room.partyId) : joinRoom(who.token, room.partyId);
  const step = stepAt(elapsedSec(exec.scenario.startTime, CHURN_START));
  actionMs.add(res.timings.duration, { step });
  const skipped = (leaving && res.status === 403 && String(res.body).includes('NOT_PARTY_MEMBER'))
    || (!leaving && res.status === 409 && /ALREADY_JOINED_OTHER_PARTY|ALREADY_JOINED_PARTY/.test(String(res.body)));
  const ok = res.status === (leaving ? 204 : 200);
  actions.add(1, { kind: leaving ? 'leave' : 'join', result: ok ? 'ok' : skipped ? 'skipped' : 'other', step });
  if (!ok && !skipped && actionWarned++ < 20) console.error(`${leaving ? 'leave' : 'join'} ${res.status}: ${res.body}`);
}

export function teardown(data) {
  for (const room of [...data.rooms, ...data.probes]) {
    const members = groupUsers(users, room.g);
    for (const u of [members[2], members[1], members[0]]) leaveRoom(u.token, room.partyId);
  }
}
