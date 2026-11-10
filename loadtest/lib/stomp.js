// k6/ws 위에 얹은 최소 STOMP 1.2 클라이언트 - 앱(ChatSocket.kt · ChatRoute.kt)이 채팅 화면에서 하는 일을 그대로 따라 한다.
//   · CONNECT(Authorization 헤더, heartbeat 없음) → /sub/chat-rooms/{id} 와 /user/queue/errors 구독
//   · 메시지 보내기와 읽음 지점 올리기: SEND_VIA=rest(기본)면 REST, stomp 면 예전처럼 소켓 SEND
//     소켓 서버(socket-app)는 SEND 를 거절하므로 분리 전후를 같은 조건으로 재려면 rest 로 둔다. stomp 는 옛 측정 재현용
//   · WebSocket ping 을 20초마다 보낸다 - 앱(OkHttp pingInterval 20s)과 같다. 없으면 ALB 유휴 60초에 끊겨 장애로 잘못 잡힌다
//   · 폴링 안전망: 소켓으로 메시지를 "한 번이라도 받기 전"에는 3초, 받은 뒤에는 20초
//     폴링으로 새로 받은 메시지는 소켓이 놓친 것이다(cursor 는 소켓으로 받을 때마다 올라가므로) - onPollRecovered 로 센다
//   · 채팅 화면은 대기 화면 위에 쌓인다. 폴링 앱은 그 아래에서 방 상세를 4초마다 폴링해 방이 닫혔는지 본다
//     (SSE 앱은 SSE 로 받는다 - k6 는 소켓과 SSE 를 같이 못 붙잡으므로 partyId 를 주지 않는다)
import ws from 'k6/ws';
import { BASE, pollChat, lastMessageId, roomDetail, sendChatRest, readChatRest } from './api.js';

const NUL = String.fromCharCode(0);   // STOMP 프레임 종료 문자
const POLL_BEFORE_REALTIME = 3;       // ChatRoute.POLL_INTERVAL_MS
const POLL_REALTIME = 20;             // ChatRoute.POLL_INTERVAL_REALTIME_MS
const DETAIL_POLL = 4;                // MatchWaitingRoute.PARTY_POLL_INTERVAL_MS
const PING_SEC = 20;                  // OkHttp pingInterval
const CLOSED = ['FINISHED', 'CANCELED'];
const SEND_VIA = __ENV.SEND_VIA || 'rest';

export function wsUrl() {
  return `${BASE.replace(/^http/, 'ws')}/ws-chat`;
}

function frame(command, headers, body = '') {
  const head = Object.keys(headers).map((k) => `${k}:${headers[k]}`).join('\n');
  return `${command}\n${head}\n\n${body}${NUL}`;
}

/** ERROR 프레임의 message 헤더 - 서버가 넣는 고정 코드(TOKEN_EXPIRED, SERVER_UNAVAILABLE, NOT_PARTICIPANT 등) */
function errorCode(data) {
  const m = data.match(/^message:(.*)$/m);
  return m ? m[1].trim() : 'UNKNOWN';
}

/**
 * 채팅방 하나에 붙어 holdSec 동안 머문다.
 *   sendEverySec       메시지 전송 주기(0 이면 듣기만). 본문은 "lt:<보낸 시각 ms>:<보낸 사람>" - 보내는 쪽과 받는 쪽이 같은 장비라 시계가 같다
 *   cursor             채팅방을 열 때 읽은 마지막 메시지 id (openChatRoom 의 결과)
 *   partyId            주면 방 상세를 4초마다 폴링하고, FINISHED·CANCELED 를 보면 소켓을 닫는다(다른 사람이 합승 완료를 누른 경우)
 *   onInversion        소켓으로 받은 메시지 id 가 직전까지 받은 가장 큰 id 보다 작으면 호출 - 순서가 뒤집힌 것
 *   onSend(ok)         메시지를 보낼 때마다 성공 여부로 호출 (rest 일 때만 의미 있음)
 *   onPollRecovered(n) 폴링에서 소켓이 놓친 메시지 n 건을 찾았을 때 호출
 * 돌려주는 값: { res, closedBy, errorCode, cursor } - cursor 는 세션이 끝날 때까지 읽은 마지막 메시지 id(재연결 때 이어 쓴다)
 *   closedBy  'timeout'      holdSec 이 다 돼서 우리가 닫음 (정상)
 *             'party-closed' 방이 끝나서 우리가 닫음 (정상)
 *             'error'        서버가 STOMP ERROR 프레임을 보냄 - errorCode 에 사유
 *             'unexpected'   우리가 닫지 않았는데 끊김 - 서버 종료·배포 등
 *             'handshake'    연결 자체가 안 됨(101 이 아님) - 서버가 내려가 있는 동안
 */
export function chatSession(token, chatRoomId, who, {
  holdSec = 30, sendEverySec = 15, cursor = null, partyId = null,
  onLatency, onError, onConnected, onInversion, onSend, onPollRecovered,
} = {}) {
  let closedBy = null;
  let lastErrorCode = null;
  const closeBy = (socket, reason) => { if (!closedBy) closedBy = reason; socket.close(); };

  let res = null;
  try {
    res = ws.connect(wsUrl(), { tags: { name: 'WS /ws-chat' } }, (socket) => {
      let realtimeAlive = false;      // 앱과 같다 - 소켓으로 메시지를 받아야 비로소 true
      let sinceChatPoll = 0, sinceDetailPoll = 0;
      let lastSeenId = 0;             // 소켓으로 받은 가장 큰 메시지 id

      const markRead = (id) => {
        if (!id || id === cursor) return;
        cursor = Math.max(cursor || 0, id);
        if (SEND_VIA === 'stomp') {
          socket.send(frame('SEND', { destination: `/pub/chat-rooms/${chatRoomId}/read`, 'content-type': 'application/json' },
            JSON.stringify({ lastReadMessageId: cursor })));
        } else {
          readChatRest(token, chatRoomId, cursor);
        }
      };

      const send = () => {
        const content = `lt:${Date.now()}:${who}`;
        if (SEND_VIA === 'stomp') {
          socket.send(frame('SEND', { destination: `/pub/chat-rooms/${chatRoomId}/messages`, 'content-type': 'application/json' },
            JSON.stringify({ content })));
        } else {
          const r = sendChatRest(token, chatRoomId, content);
          if (onSend) onSend(r.status >= 200 && r.status < 300);
        }
      };

      socket.on('open', () => {
        socket.send(frame('CONNECT', { 'accept-version': '1.2', host: 'moyeota', Authorization: `Bearer ${token}`, 'heart-beat': '0,0' }));
        socket.setInterval(() => socket.ping(), PING_SEC * 1000);
      });

      socket.on('message', (data) => {
        const command = data.substring(0, data.indexOf('\n'));
        if (command === 'CONNECTED') {
          if (onConnected) onConnected();
          socket.send(frame('SUBSCRIBE', { id: 'sub-0', destination: `/sub/chat-rooms/${chatRoomId}` }));
          socket.send(frame('SUBSCRIBE', { id: 'sub-err', destination: '/user/queue/errors' }));
          if (sendEverySec > 0) {
            socket.setTimeout(send, 1000 + Math.random() * 2000);       // 첫 메시지가 접속 직후 몰리지 않게 흩뜨린다
            socket.setInterval(send, sendEverySec * 1000);
          }
        } else if (command === 'MESSAGE') {
          realtimeAlive = true;
          const sent = data.match(/"content":"lt:(\d+):/);
          if (sent && onLatency) onLatency(Date.now() - Number(sent[1]));
          const id = data.match(/"id":(\d+)/);
          if (id) {
            const messageId = Number(id[1]);
            if (messageId < lastSeenId && onInversion) onInversion();
            lastSeenId = Math.max(lastSeenId, messageId);
            markRead(messageId);
          }
        } else if (command === 'ERROR') {
          lastErrorCode = errorCode(data);
          if (onError) onError(`ERROR ${lastErrorCode}`);
          closeBy(socket, 'error');
        }
      });

      // 1초 틱으로 두 폴링 루프를 흉내 낸다 (k6/ws 콜백 안에서는 http 호출이 된다)
      socket.setInterval(() => {
        sinceChatPoll++; sinceDetailPoll++;
        if (sinceChatPoll >= (realtimeAlive ? POLL_REALTIME : POLL_BEFORE_REALTIME)) {
          sinceChatPoll = 0;
          const before = cursor;
          const polled = pollChat(token, chatRoomId, cursor);
          if (before && polled.status === 200 && onPollRecovered) {
            const missed = polled.json('messages').filter((m) => m.id > before).length;
            if (missed > 0) onPollRecovered(missed);
          }
          markRead(lastMessageId(polled, cursor));
        }
        if (partyId && sinceDetailPoll >= DETAIL_POLL) {
          sinceDetailPoll = 0;
          const detail = roomDetail(token, partyId);
          if (detail.status === 200 && CLOSED.includes(detail.json('status'))) closeBy(socket, 'party-closed');
        }
      }, 1000);

      // 비정상 종료 때 k6 는 error 를 먼저 내고 close 를 낸다. 여기서 사유를 정하면 배포 끊김이 'error' 로 섞이므로 기록만 한다
      socket.on('error', (e) => { if (onError) onError(String(e.error ? e.error() : e)); });
      socket.on('close', () => { if (!closedBy) closedBy = 'unexpected'; });   // 우리가 닫은 게 아니면 서버 쪽에서 끊은 것
      socket.setTimeout(() => closeBy(socket, 'timeout'), holdSec * 1000);
    });
  } catch (e) {
    // 핸드셰이크가 101 이 아니면(ALB 502·503, 연결 거부) k6 는 예외를 던진다
    if (onError) onError(`handshake ${e}`);
  }

  if (!res || res.status !== 101) closedBy = 'handshake';
  return { res, closedBy: closedBy || 'unexpected', errorCode: lastErrorCode, cursor };
}
