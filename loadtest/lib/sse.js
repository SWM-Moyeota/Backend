// 대기 화면(21)의 SSE 구독 - 서버 GET /matching/rooms/{id}/events 를 xk6-sse 로 붙잡는다.
//   서버는 구독 직후 `connected`(방 상세 전체), 멤버 변화에 `member-joined`·`member-left`(변경분), 방이 닫히면 `closed` 를 보내고
//   30초마다 `:ping` 주석을 흘린다. 앱은 변경분을 들고 있는 목록에 더하고 뺄 뿐 방 상세를 다시 읽지 않는다.
//   xk6-sse 는 주석도 event 콜백으로 올린다(name 이 빈 문자열) - 그걸 틱으로 써서 holdSec 뒤에 닫는다.
//   읽기 에러가 나면 반드시 client.close() 를 불러야 open() 이 돌아온다 (안 부르면 VU 가 끝날 때까지 블로킹).
import sse from 'k6/x/sse';
import { Trend, Counter } from 'k6/metrics';
import { BASE, auth } from './api.js';

export const connectMs = new Trend('sse_connect_ms', true);       // open 요청 → connected 이벤트
export const sseErrors = new Counter('sse_errors');
export const sseEvents = new Counter('sse_events');                // name 태그: connected / member-joined / member-left / closed
export const sseReconnects = new Counter('sse_reconnects');        // 에러로 끊겨 3초 뒤 다시 붙은 횟수 (앱의 재연결)
export const viewChecks = new Counter('sse_view_check');           // result 태그: match / members / status / host - 변경분으로 쌓은 화면 vs 방 상세
export const VIEW_CHECK_RESULTS = ['match', 'members', 'status', 'host'];

/** 서버 이벤트의 data 를 JSON 으로 읽는다. connected·member-* 는 객체, closed 는 방 번호 */
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
    tags: { name: 'SSE /matching/rooms/{id}/events' },
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
 * 앱이 화면에 들고 있는 방 모델. 이벤트를 받을 때마다 apply 로 갱신한다 - 앱(MatchWaitingRoute)과 같은 규칙이다.
 *   connected      방 상세 전체로 통째로 바꾼다 (처음 연결·재연결 모두). 방장은 members 의 host
 *   member-joined  더한다. 이미 있는 publicId 면 그 자리에서 교체. 상태 반영 (합류는 방장을 바꾸지 않는다)
 *   member-left    publicId 로 뺀다. 상태 반영. hostPublicId(승계 후 방장)가 남은 목록에 있으면 방장을 옮긴다
 *   closed         닫힘
 * 앱이 상세를 1회 다시 읽는 경우(나간 사람·지목된 방장이 목록에 없음, 목록이 상태와 모순)는 resync 로만 표시한다 - k6 는 읽지 않는다
 */
export function newRoomView() {
  return { status: null, members: new Map(), host: null, capacity: 0, closed: false, resync: false };
}

function hostOf(members) {
  const h = (members || []).find((m) => m.host);
  return h ? h.publicId : null;
}

export function applyEvent(view, name, data) {
  if (name === 'connected' && data && typeof data === 'object') {
    view.status = data.status;
    view.capacity = data.capacity || 0;
    view.members = new Map((data.members || []).map((m) => [m.publicId, m]));
    view.host = hostOf(data.members);
    view.resync = false;
  } else if (name === 'member-joined' && data && typeof data === 'object') {
    const m = data.member || {};
    if (m.publicId) view.members.set(m.publicId, m);
    if (data.status) view.status = data.status;
  } else if (name === 'member-left' && data && typeof data === 'object') {
    if (data.publicId) view.members.delete(data.publicId);
    else view.resync = true;                                       // 누가 나갔는지 모른다
    if (data.status) view.status = data.status;
    if (data.hostPublicId && view.members.has(data.hostPublicId)) view.host = data.hostPublicId;
    else view.resync = true;                                       // null 이거나 목록에 없다(순서 역전) - 방장 표시는 그대로
  } else if (name === 'closed') {
    view.closed = true;
  }
  const n = view.members.size;
  if (view.capacity > 0 && ((view.status === 'COMPLETED' && n < view.capacity) || (view.status === 'ACTIVE' && n >= view.capacity))) view.resync = true;
  return view;
}

const WARN_MAX = 5;                                                // VU 당 어긋남 로그 상한
let warned = 0;
const short = (id) => (id ? String(id).slice(0, 8) : '-');

/**
 * 변경분으로 쌓은 화면(view)을 방 상세(roomDetail 응답)와 맞춰 sse_view_check 에 남긴다.
 * 멤버 publicId 집합 → status → 방장 순으로 보고 처음 어긋난 것 하나를 result 로 단다. 상세를 못 읽었거나 connected 전이면 기록하지 않는다
 */
export function compareWithDetail(view, detail) {
  if (!detail || detail.status !== 200 || view.status === null) return null;
  const d = detail.json();
  const mine = [...view.members.keys()].filter(Boolean).sort();
  const theirs = (d.members || []).map((m) => m.publicId).filter(Boolean).sort();
  const host = hostOf(d.members);
  const result = mine.join() !== theirs.join() ? 'members' : view.status !== d.status ? 'status' : view.host !== host ? 'host' : 'match';
  viewChecks.add(1, { result });
  if (result !== 'match' && warned++ < WARN_MAX) {
    console.warn(`SSE 화면≠상세 partyId=${d.id} ${result}: 화면 ${view.status} [${mine.map(short)}] 방장 ${short(view.host)}${view.resync ? ' (앱이면 상세 재조회)' : ''}`
      + ` / 상세 ${d.status} [${theirs.map(short)}] 방장 ${short(host)}`);
  }
  return result;
}
