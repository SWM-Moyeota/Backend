// 대기 화면(21)의 SSE 구독 - 서버 GET /matching/rooms/{id}/events 를 xk6-sse 로 붙잡는다.
//   서버는 구독 직후 `connected`(방 상세 전체, 끝에 version), 멤버 변화에 `member-joined`·`member-left`(변경분 + version),
//   방이 닫히면 `closed`({ status }, 옛 서버는 방 번호나 { partyId, status, version })를 보낸다. version 은 방이 바뀔 때마다 정확히 1 오른다(생성 시 0).
//   30초마다 `:ping` 주석이 흐른다(연결 유지).
//   앱은 version 이 하나씩 이어지는 변경분만 들고 있는 목록에 반영하고, 번호가 빠지는 등 화면을 믿을 수 없으면
//   방 상세를 다시 읽는다(resync) - 스트림은 끊지 않는다.
//   xk6-sse 는 주석도 event 콜백으로 올린다(name 이 빈 문자열) - 그걸 틱으로 써서 holdSec 뒤에 닫는다.
//   읽기 에러가 나면 반드시 client.close() 를 불러야 open() 이 돌아온다 (안 부르면 VU 가 끝날 때까지 블로킹).
import sse from 'k6/x/sse';
import { Trend, Counter } from 'k6/metrics';
import { BASE, auth, roomDetail } from './api.js';

export const connectMs = new Trend('sse_connect_ms', true);       // open 요청 → connected 이벤트
export const sseErrors = new Counter('sse_errors');
export const sseEvents = new Counter('sse_events');                // name 태그: connected / member-joined / member-left / closed
export const sseReconnects = new Counter('sse_reconnects');        // 에러로 끊겨 3초 뒤 다시 붙은 횟수 (앱의 재연결)
export const viewChecks = new Counter('sse_view_check');           // result 태그: match / members / status / host / version - 변경분으로 쌓은 화면 vs 방 상세
export const VIEW_CHECK_RESULTS = ['match', 'members', 'status', 'host', 'version'];
export const resyncs = new Counter('sse_resync');
export const RESYNC_REASONS = ['gap', 'unknown-member', 'unknown-host', 'drift'];
export const resyncResults = new Counter('sse_resync_result');
export const RESYNC_RESULTS = ['applied', 'stale', 'failed'];
export const staleEvents = new Counter('sse_stale_event');
// 전파 지연 - 같은 VU 가 join 직전과 member-joined 수신을 찍는다(시계 차이 없음). 서버 쪽 Timer 는 없다 - k6 로만 잰다 (D7 propagation)
export const propagationMs = new Trend('sse_propagation_ms', true);

const SSE_NAME = 'SSE /matching/rooms/{id}/events';
const RESYNC = 'GET /matching/rooms/{id} (resync)';
const isVersion = Number.isInteger;

/** 서버 이벤트의 data 를 JSON 으로 읽는다. connected·member-*·closed 는 객체, 옛 서버의 closed 는 방 번호(숫자) */
function parse(data) {
  try { return JSON.parse(data); } catch (e) { return data; }
}

/**
 * partyId 방의 변화를 holdSec 동안 듣는다.
 *   onEvent(name, data, client)  connected / member-joined / member-left / closed 마다. data 는 JSON 을 읽은 값.
 *                                false 를 돌려주거나 client.close() 로 일찍 끝낼 수 있다
 * 돌려주는 값 { status, closedBy }  closedBy: 'hold' | 'closed' | 'early' | 'error'
 */
export function watchParty(token, partyId, { holdSec = 60, onEvent } = {}) {
  const started = Date.now();
  let closedBy = 'hold';
  let connected = false;

  const res = sse.open(`${BASE}/api/v1/matching/rooms/${partyId}/events`, {
    headers: auth(token).headers,
    tags: { name: SSE_NAME, url: SSE_NAME },
    timeout: `${Math.ceil(holdSec) + 60}s`,                                   // 안전망 - 주석 틱이 끊겨도 언젠가는 돌아온다
  }, (client) => {
    client.on('event', (ev) => {
      if (!ev.name) {                                              // :ping 주석
        if (Date.now() - started >= holdSec * 1000) client.close();
        return;
      }
      sseEvents.add(1, { name: ev.name });
      const data = parse(ev.data);
      if (ev.name === 'connected' && !connected) { connected = true; connectMs.add(Date.now() - started); }
      if (onEvent && onEvent(ev.name, data, client) === false) { closedBy = 'early'; client.close(); return; }
      if (ev.name === 'closed') { closedBy = 'closed'; client.close(); }
    });
    client.on('error', (e) => {
      closedBy = 'error';
      sseErrors.add(1);
      console.error(`SSE partyId=${partyId}: ${e.error ? e.error() : e}`);
      client.close();
    });
  });

  if (!res || res.error) { closedBy = 'error'; sseErrors.add(1); }
  return { status: res ? res.status : 0, closedBy };
}

/**
 * 앱이 화면에 들고 있는 방 모델. 이벤트를 받을 때마다 follow 로 갱신한다 - 앱(MatchWaitingRoute)과 같은 규칙이다.
 *   connected      version 이 화면 이상이면(화면에 version 이 없으면 무조건) 방 상세 전체로 통째로 바꾼다. 낮으면 버린다(sse_stale_event)
 *   member-*       version 이 화면+1 이면 반영하고 화면 version 을 올린다. 화면 이하면 버린다(sse_stale_event).
 *                  connected 전이거나, 그보다 크거나, 정수가 아니면 resync:gap
 *     member-joined  더한다. 이미 있는 publicId 면 그 자리에서 교체. 상태 반영 (합류는 방장을 바꾸지 않는다)
 *     member-left    publicId 로 뺀다. 상태 반영. hostPublicId(승계 후 방장)가 남은 목록에 있으면 방장을 옮긴다
 *   closed         version 과 상관없이 닫힘. JSON 이면 status 도 반영
 * 반영한 변경분이 화면을 믿을 수 없게 만들면 그 이유로 resync 한다 - 나간·들어온 사람을 모름(unknown-member),
 * 지목된 방장이 null 이거나 목록에 없음(unknown-host), 목록이 상태와 모순(drift).
 * resync 는 상세를 다시 읽어(GET /matching/rooms/{id} (resync)) 그 version 이 화면 이상일 때만 통째로 바꾼다 - sse_resync{reason}, sse_resync_result{result}
 */
export function newRoomView() {
  return { version: null, status: null, members: new Map(), host: null, capacity: 0, closed: false };
}

function hostOf(members) {
  const h = (members || []).find((m) => m.host);
  return h ? h.publicId : null;
}

function notOlder(view, d) {
  return !isVersion(view.version) || (isVersion(d.version) && d.version >= view.version);
}

function replace(view, d) {
  view.version = isVersion(d.version) ? d.version : null;
  view.status = d.status;
  view.capacity = d.capacity || 0;
  view.members = new Map((d.members || []).map((m) => [m.publicId, m]));
  view.host = hostOf(d.members);
}

function applyMember(view, name, data) {
  if (!isVersion(view.version) || !isVersion(data.version)) return 'gap';
  if (data.version <= view.version) { staleEvents.add(1, { name }); return null; }
  if (data.version !== view.version + 1) return 'gap';
  view.version = data.version;
  if (data.status) view.status = data.status;
  let reason = null;
  if (name === 'member-joined') {
    const m = data.member || {};
    if (m.publicId) view.members.set(m.publicId, m);
    else reason = 'unknown-member';
  } else {
    if (data.publicId && view.members.has(data.publicId)) view.members.delete(data.publicId);
    else reason = 'unknown-member';
    if (data.hostPublicId && view.members.has(data.hostPublicId)) view.host = data.hostPublicId;
    else reason = reason || 'unknown-host';
  }
  const n = view.members.size;
  if (!reason && view.capacity > 0 && ((view.status === 'COMPLETED' && n < view.capacity) || (view.status === 'ACTIVE' && n >= view.capacity))) reason = 'drift';
  return reason;
}

export function applyEvent(view, name, data) {
  const obj = data && typeof data === 'object' ? data : null;
  if (name === 'connected') {
    if (!obj) return null;
    if (notOlder(view, obj)) replace(view, obj);
    else staleEvents.add(1, { name });
    return null;
  }
  if (name === 'member-joined' || name === 'member-left') return obj ? applyMember(view, name, obj) : 'gap';
  if (name === 'closed') {
    view.closed = true;
    if (obj && typeof obj.status === 'string') view.status = obj.status;
    if (obj && isVersion(obj.version) && (!isVersion(view.version) || obj.version > view.version)) view.version = obj.version;
  }
  return null;
}

export function resync(view, token, partyId, reason) {
  resyncs.add(1, { reason });
  const res = roomDetail(token, partyId, RESYNC);
  let d = null;
  if (res.status === 200) {
    try { d = res.json(); } catch (e) { d = null; }
  }
  let result = 'failed';
  if (d && typeof d === 'object') {
    result = notOlder(view, d) ? 'applied' : 'stale';
    if (result === 'applied') replace(view, d);
  }
  resyncResults.add(1, { result });
  return result;
}

export function follow(view, token, partyId, name, data) {
  const reason = applyEvent(view, name, data);
  if (reason) resync(view, token, partyId, reason);
  return view;
}

const WARN_MAX = 5;                                                // VU 당 어긋남 로그 상한
let warned = 0;
const short = (id) => (id ? String(id).slice(0, 8) : '-');

/**
 * 변경분으로 쌓은 화면(view)을 방 상세(roomDetail 응답)와 맞춰 sse_view_check 에 남긴다.
 * 멤버 publicId 집합 → status → 방장 → version 순으로 보고 처음 어긋난 것 하나를 result 로 단다. 상세를 못 읽었거나 connected 전이면 기록하지 않는다
 */
export function compareWithDetail(view, detail) {
  if (!detail || detail.status !== 200 || view.status === null) return null;
  const d = detail.json();
  const mine = [...view.members.keys()].filter(Boolean).sort();
  const theirs = (d.members || []).map((m) => m.publicId).filter(Boolean).sort();
  const host = hostOf(d.members);
  const version = isVersion(d.version) ? d.version : null;
  const result = mine.join() !== theirs.join() ? 'members'
    : view.status !== d.status ? 'status'
      : view.host !== host ? 'host'
        : view.version !== version ? 'version' : 'match';
  viewChecks.add(1, { result });
  if (result !== 'match' && warned++ < WARN_MAX) {
    console.warn(`SSE 화면≠상세 partyId=${d.id} ${result}: 화면 v${view.version} ${view.status} [${mine.map(short)}] 방장 ${short(view.host)}`
      + ` / 상세 v${version} ${d.status} [${theirs.map(short)}] 방장 ${short(host)}`);
  }
  return result;
}
