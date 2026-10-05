// k6/ws 위에 얹은 최소 STOMP 1.2 클라이언트 - 앱(ChatSocket.kt · ChatRoute.kt)이 채팅 화면에서 하는 일을 그대로 따라 한다.
//   · CONNECT(Authorization 헤더, heartbeat 없음) → /sub/chat-rooms/{id} 와 /user/queue/errors 구독
//   · 메시지는 소켓으로 보내고, 새 메시지를 받을 때마다 읽음 지점을 소켓으로 올린다(/pub/.../read)
//   · 폴링 안전망: 소켓으로 메시지를 "한 번이라도 받기 전"에는 3초, 받은 뒤에는 20초
//   · 채팅 화면은 대기 화면 위에 쌓인다. 그 아래에서 방이 닫혔는지 확인하는 방식은 둘이다
//       statusPollSec 을 주면   방 상태(/status)를 그 주기로만 확인하고, 지문이 달라졌을 때만 방 상세를 다시 읽는다 (SSE 를 쓰는 앱의 안전망)
//       안 주면                 방 상세를 4초마다 폴링한다 (SSE 를 쓰기 전의 앱)
import ws from 'k6/ws';
import { BASE, pollChat, lastMessageId, roomDetail, roomStatus } from './api.js';

const NUL = String.fromCharCode(0);   // STOMP 프레임 종료 문자
const POLL_BEFORE_REALTIME = 3;       // ChatRoute.POLL_INTERVAL_MS
const POLL_REALTIME = 20;             // ChatRoute.POLL_INTERVAL_REALTIME_MS
const DETAIL_POLL = 4;                // MatchWaitingRoute.PARTY_POLL_INTERVAL_MS
const CLOSED = ['FINISHED', 'CANCELED'];

export function wsUrl() {
  return `${BASE.replace(/^http/, 'ws')}/ws-chat`;
}

function frame(command, headers, body = '') {
  const head = Object.keys(headers).map((k) => `${k}:${headers[k]}`).join('\n');
  return `${command}\n${head}\n\n${body}${NUL}`;
}

/**
 * 채팅방 하나에 붙어 holdSec 동안 머문다.
 *   sendEverySec  메시지 전송 주기(0 이면 듣기만). 본문은 "lt:<보낸 시각 ms>:<보낸 사람>" - 보내는 쪽과 받는 쪽이 같은 장비라 시계가 같다
 *   cursor        채팅방을 열 때 읽은 마지막 메시지 id (openChatRoom 의 결과)
 *   partyId       주면 방이 닫혔는지 확인하고, FINISHED·CANCELED 를 보면 소켓을 닫는다(다른 사람이 합승 완료를 누른 경우)
 *   statusPollSec 주면 방 상태(/status)를 이 주기로 확인한다. 안 주면 방 상세를 4초마다 폴링한다
 *   fingerprint   화면에 그려 둔 방 상세의 지문. 방 상태의 지문이 이와 다르면 방 상세를 한 번 다시 읽는다
 * 돌려주는 값: { res, closedBy } - closedBy 는 'timeout' | 'party-closed' | 'error'
 */
export function chatSession(token, chatRoomId, who, {
  holdSec = 30, sendEverySec = 15, cursor = null, partyId = null, statusPollSec = null, fingerprint = null, onLatency, onError, onConnected,
} = {}) {
  let closedBy = 'timeout';
  const res = ws.connect(wsUrl(), { tags: { name: 'WS /ws-chat' } }, (socket) => {
    let realtimeAlive = false;      // 앱과 같다 - 소켓으로 메시지를 받아야 비로소 true
    let sinceChatPoll = 0, sinceDetailPoll = 0;

    const markRead = (id) => {
      if (!id || id === cursor) return;
      cursor = Math.max(cursor || 0, id);
      socket.send(frame('SEND', { destination: `/pub/chat-rooms/${chatRoomId}/read`, 'content-type': 'application/json' },
        JSON.stringify({ lastReadMessageId: cursor })));
    };

    socket.on('open', () => {
      socket.send(frame('CONNECT', { 'accept-version': '1.2', host: 'moyeota', Authorization: `Bearer ${token}`, 'heart-beat': '0,0' }));
    });

    socket.on('message', (data) => {
      const command = data.substring(0, data.indexOf('\n'));
      if (command === 'CONNECTED') {
        if (onConnected) onConnected();
        socket.send(frame('SUBSCRIBE', { id: 'sub-0', destination: `/sub/chat-rooms/${chatRoomId}` }));
        socket.send(frame('SUBSCRIBE', { id: 'sub-err', destination: '/user/queue/errors' }));
        if (sendEverySec > 0) {
          const send = () => socket.send(frame('SEND',
            { destination: `/pub/chat-rooms/${chatRoomId}/messages`, 'content-type': 'application/json' },
            JSON.stringify({ content: `lt:${Date.now()}:${who}` })));
          socket.setTimeout(send, 1000 + Math.random() * 2000);       // 첫 메시지가 접속 직후 몰리지 않게 흩뜨린다
          socket.setInterval(send, sendEverySec * 1000);
        }
      } else if (command === 'MESSAGE') {
        realtimeAlive = true;
        const sent = data.match(/"content":"lt:(\d+):/);
        if (sent && onLatency) onLatency(Date.now() - Number(sent[1]));
        const id = data.match(/"id":(\d+)/);
        if (id) markRead(Number(id[1]));
      } else if (command === 'ERROR') {
        closedBy = 'error';
        if (onError) onError(data.substring(0, 300));
        socket.close();
      }
    });

    // 1초 틱으로 두 폴링 루프를 흉내 낸다 (k6/ws 콜백 안에서는 http 호출이 된다)
    socket.setInterval(() => {
      sinceChatPoll++; sinceDetailPoll++;
      if (sinceChatPoll >= (realtimeAlive ? POLL_REALTIME : POLL_BEFORE_REALTIME)) {
        sinceChatPoll = 0;
        markRead(lastMessageId(pollChat(token, chatRoomId, cursor), cursor));
      }
      if (partyId && statusPollSec && sinceDetailPoll >= statusPollSec) {
        sinceDetailPoll = 0;
        const st = roomStatus(token, partyId);
        if (st.status === 200) {
          if (CLOSED.includes(st.json('status'))) { closedBy = 'party-closed'; socket.close(); }
          else if ((st.json('fingerprint') || null) !== fingerprint) {       // 놓친 변화가 있다 - 그때만 무거운 상세를 읽는다
            const detail = roomDetail(token, partyId);
            if (detail.status === 200) fingerprint = detail.json('fingerprint') || null;
          }
        }
      }
      if (partyId && !statusPollSec && sinceDetailPoll >= DETAIL_POLL) {
        sinceDetailPoll = 0;
        const detail = roomDetail(token, partyId);
        if (detail.status === 200 && CLOSED.includes(detail.json('status'))) { closedBy = 'party-closed'; socket.close(); }
      }
    }, 1000);

    socket.on('error', (e) => { closedBy = 'error'; if (onError) onError(String(e.error ? e.error() : e)); });
    socket.setTimeout(() => socket.close(), holdSec * 1000);
  });
  return { res, closedBy };
}
