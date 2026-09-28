// 대기 화면(21)의 SSE 구독 - 서버 GET /matching/rooms/{id}/events 를 xk6-sse 로 붙잡는다.
//   서버는 구독 직후 `connected`, 멤버 변화에 `changed`, 방이 닫히면 `closed` 를 보내고 15초마다 `:ping` 주석을 흘린다.
//   xk6-sse 는 주석도 event 콜백으로 올린다(name 이 빈 문자열) - 그걸 15초 틱으로 써서 holdSec 뒤에 닫는다.
//   읽기 에러가 나면 반드시 client.close() 를 불러야 open() 이 돌아온다 (안 부르면 VU 가 끝날 때까지 블로킹).
import sse from 'k6/x/sse';
import { Trend, Counter } from 'k6/metrics';
import { BASE, auth } from './api.js';

export const connectMs = new Trend('sse_connect_ms', true);       // open 요청 → connected 이벤트
export const sseErrors = new Counter('sse_errors');
export const sseEvents = new Counter('sse_events');                // name 태그: connected / changed / closed
export const propagationOf = new Trend('sse_propagation_ms', true); // 남이 join 한 시각(서버 joinedAt) → 내 SSE 에 changed 도착

/**
 * partyId 방의 변화를 holdSec 동안 듣는다.
 *   onEvent(name, data, client)  connected / changed / closed 마다. client.close() 로 일찍 끝낼 수 있다
 * 돌려주는 값 { status, closedBy }  closedBy: 'hold' | 'closed' | 'early' | 'error'
 */
export function watchParty(token, partyId, { holdSec = 60, onEvent } = {}) {
  const started = Date.now();
  let closedBy = 'hold';
  let connected = false;

  const res = sse.open(`${BASE}/api/v1/matching/rooms/${partyId}/events`, {
    headers: auth(token).headers,
    tags: { name: 'SSE /matching/rooms/{id}/events' },
    timeout: `${holdSec + 30}s`,                                   // 안전망 - 주석 틱이 끊겨도 언젠가는 돌아온다
  }, (client) => {
    client.on('event', (ev) => {
      if (!ev.name) {                                              // :ping 주석 = 15초 틱
        if (Date.now() - started >= holdSec * 1000) client.close();
        return;
      }
      sseEvents.add(1, { name: ev.name });
      if (ev.name === 'connected' && !connected) { connected = true; connectMs.add(Date.now() - started); }
      if (ev.name === 'closed') { closedBy = 'closed'; client.close(); return; }
      if (onEvent && onEvent(ev.name, ev.data, client) === false) { closedBy = 'early'; client.close(); }
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

/** 응답의 멤버 중 가장 최근 joinedAt (ms). 반영 지연 = Date.now() - 이 값. 서버·k6 시계가 같은 AWS NTP 라 ms 오차 */
export function latestJoinedAt(detailRes) {
  if (detailRes.status !== 200) return null;
  const ts = detailRes.json('members').map((m) => Date.parse(m.joinedAt)).filter((t) => !isNaN(t));
  return ts.length ? Math.max(...ts) : null;
}
