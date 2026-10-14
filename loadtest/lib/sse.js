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
    timeout: `${holdSec + 60}s`,                                   // 안전망 - 주석 틱이 끊겨도 언젠가는 돌아온다
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
 * 앱이 화면에 들고 있는 방 모델. 이벤트를 받을 때마다 apply 로 갱신한다 - 앱과 같은 규칙이다.
 *   connected      방 상세 전체로 통째로 바꾼다 (처음 연결·재연결 모두)
 *   member-joined  이미 있는 publicId 면 목록은 그대로, 상태만 반영
 *   member-left    없는 publicId 면 목록은 그대로, 상태만 반영
 *   closed         닫힘
 */
export function newRoomView() {
  return { status: null, members: new Map(), closed: false };
}

export function applyEvent(view, name, data) {
  if (name === 'connected' && data && typeof data === 'object') {
    view.status = data.status;
    view.members = new Map((data.members || []).map((m) => [m.publicId, m]));
  } else if (name === 'member-joined' && data && typeof data === 'object') {
    const m = data.member || {};
    if (m.publicId && !view.members.has(m.publicId)) view.members.set(m.publicId, m);
    if (data.status) view.status = data.status;
  } else if (name === 'member-left' && data && typeof data === 'object') {
    if (data.publicId) view.members.delete(data.publicId);
    if (data.status) view.status = data.status;
  } else if (name === 'closed') {
    view.closed = true;
  }
  return view;
}
