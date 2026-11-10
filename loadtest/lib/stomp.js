// k6/ws 위에 얹은 최소 STOMP 1.2 클라이언트 - 앱(ChatSocket.kt · ChatRoute.kt)이 채팅 화면에서 하는 일을 그대로 따라 한다.
//   · CONNECT(Authorization 헤더, heartbeat 없음) → /sub/chat-rooms/{id} 와 /user/queue/errors 구독
//   · 메시지 보내기와 읽음 지점 올리기: SEND_VIA=rest(기본)면 REST, stomp 면 예전처럼 소켓 SEND
//     소켓 서버(socket-app)는 SEND 를 거절하므로 분리 전후를 같은 조건으로 재려면 rest 로 둔다. stomp 는 옛 측정 재현용
//   · WebSocket ping 을 20초마다 보낸다 - 앱(OkHttp pingInterval 20s)과 같다. 없으면 ALB 유휴 60초에 끊겨 장애로 잘못 잡힌다
//   · 폴링 안전망: 소켓으로 메시지를 "한 번이라도 받기 전"에는 3초, 받은 뒤에는 20초
//     폴링으로 받았는데 세션이 끝날 때까지 소켓으로 안 온 메시지가 소켓이 놓친 것이다 - onPollRecovered 로 센다
//   · 채팅 화면은 대기 화면 위에 쌓인다. 폴링 앱은 그 아래에서 방 상세를 4초마다 폴링해 방이 닫혔는지 본다
//     (SSE 앱은 SSE 로 받는다 - k6 는 소켓과 SSE 를 같이 못 붙잡으므로 partyId 를 주지 않는다)
import ws from 'k6/ws';
import { Counter, Trend } from 'k6/metrics';
import { BASE, pollChat, lastMessageId, roomDetail, sendChatRest, readChatRest } from './api.js';

const NUL = String.fromCharCode(0);   // STOMP 프레임 종료 문자
const POLL_BEFORE_REALTIME = 3;       // ChatRoute.POLL_INTERVAL_MS
const POLL_REALTIME = 20;             // ChatRoute.POLL_INTERVAL_REALTIME_MS
const DETAIL_POLL = 4;                // MatchWaitingRoute.PARTY_POLL_INTERVAL_MS
const PING_SEC = 20;                  // OkHttp pingInterval
const CLOSED = ['FINISHED', 'CANCELED'];
const SEND_VIA = __ENV.SEND_VIA || 'rest';
const SEQ_GAP_WAIT_MS = Number(__ENV.SEQ_GAP_WAIT_MS || 500);   // seq 정렬 흉내에서 빈 번호를 기다리는 최대 시간
const READ_EVERY_SEC = Number(__ENV.READ_EVERY_SEC || 0);       // REST 읽음을 이 간격으로 묶는다(마지막 번호만). 0 이면 받을 때마다 - 빠른 전송 측정에서 읽음이 부하를 덮지 않게

export function wsUrl() {
  return `${BASE.replace(/^http/, 'ws')}/ws-chat`;
}

function frame(command, headers, body = '') {
  const head = Object.keys(headers).map((k) => `${k}:${headers[k]}`).join('\n');
  return `${command}\n${head}\n\n${body}${NUL}`;
}

/**
 * seq 기준 순서 지표. 시나리오 맨 위(초기화 단계)에서 한 번 만들고 chatSession 옵션에 펼쳐 넣는다 - chatSession(..., { ...seqOrder })
 *   chat_seq_inversions              도착 순서가 seq 와 뒤집힌 횟수 (서버가 보낸 그대로의 역전)
 *   chat_seq_inversions_same_sender  그중 같은 사람이 보낸 것끼리 뒤집힌 횟수
 *   chat_seq_wait_ms                 앱이 seq 로 정렬한다고 칠 때 메시지마다 기다린 시간 - 역전을 정렬로 가리는 비용
 *   chat_seq_gap_timeout             빈 번호를 SEQ_GAP_WAIT_MS 넘게 기다려 건너뛴 횟수 (앱이라면 /after 호출)
 *   chat_seq_late                    건너뛴 뒤 늦게 온 번호 - 정렬로도 사용자에게 뒤집혀 보이는 것. 목표 0
 */
export function seqOrderMetrics() {
  const inversions = new Counter('chat_seq_inversions');
  const sameSender = new Counter('chat_seq_inversions_same_sender');
  const wait = new Trend('chat_seq_wait_ms', true);
  const gapTimeout = new Counter('chat_seq_gap_timeout');
  const late = new Counter('chat_seq_late');
  return {
    onSeqInversion: (same) => { inversions.add(1); if (same) sameSender.add(1); },
    onSeqWait: (ms) => wait.add(ms),
    onSeqGapTimeout: () => gapTimeout.add(1),
    onSeqLate: () => late.add(1),
  };
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
 *   onInversion        소켓으로 받은 메시지 id 가 직전까지 받은 가장 큰 id 보다 작으면 호출 - 순서가 뒤집힌 것 (옛 기준. id 는 순서 기준이 아니다)
 *   seq 기준 (방마다 서버가 잠금 안에서 매기는 번호 - 순서의 기준). 세션의 첫 메시지 seq 를 출발점으로 삼는다
 *   onSeqInversion(sameSender) 받은 seq 가 직전까지 받은 가장 큰 seq 보다 작으면 호출. sameSender 는 같은 사람이 보낸 것끼리 뒤집혔는지
 *   onSeqWait(ms)      클라이언트가 seq 로 정렬한다고 치고, 메시지마다 "받은 뒤 화면에 내보내기까지 기다린 시간" (제 차례면 0)
 *   onSeqGapTimeout()  빈 번호를 SEQ_GAP_WAIT_MS 넘게 기다려 건너뛴 횟수 - 앱이라면 이때 /after 로 채운다
 *   onSeqLate()        건너뛴 번호가 그 뒤에 도착 - 정렬로도 못 고친 역전
 *   onSend(ok)         메시지를 보낼 때마다 성공 여부로 호출 (rest 일 때만 의미 있음)
 *   onPollRecovered(n) 세션이 끝날 때 한 번 - 폴링으로는 받았는데 소켓으로는 끝내 안 온 메시지 n 건 (0 이면 부르지 않는다)
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
  onSeqInversion, onSeqWait, onSeqGapTimeout, onSeqLate,
} = {}) {
  let closedBy = null;
  let lastErrorCode = null;
  const closeBy = (socket, reason) => { if (!closedBy) closedBy = reason; socket.close(); };

  // 폴링으로 먼저 본 메시지 id 중 아직 소켓으로 안 온 것. 소켓으로 오면 지운다.
  // 폴링이 소켓보다 빨라 잠깐 겹치는 경우를 놓친 것으로 세지 않으려고, 세션이 끝날 때 남은 것만 onPollRecovered 로 넘긴다
  const seenIds = new Set();
  const pendingFromPoll = new Set();

  let res = null;
  try {
    res = ws.connect(wsUrl(), { tags: { name: 'WS /ws-chat' } }, (socket) => {
      let realtimeAlive = false;      // 앱과 같다 - 소켓으로 메시지를 받아야 비로소 true
      let sinceChatPoll = 0, sinceDetailPoll = 0;
      let lastSeenId = 0;             // 소켓으로 받은 가장 큰 메시지 id

      // seq 정렬 흉내 - 앱이 seq 로 줄 세워 보여 준다고 치면 사용자에게 무엇이 보이는가
      let maxSeq = 0;                 // 도착 순서대로 본 가장 큰 seq
      const maxSeqBySender = {};      // 보낸 사람별 가장 큰 seq
      let nextSeq = null;             // 다음에 화면에 내보낼 seq
      const held = new Map();         // seq → 도착 시각. 앞 번호가 아직 안 와서 붙잡아 둔 메시지
      let gapSince = null;            // 지금 빈 번호를 기다리기 시작한 시각

      const releaseInOrder = (now) => {
        let moved = false;
        while (held.has(nextSeq)) {
          if (onSeqWait) onSeqWait(now - held.get(nextSeq));
          held.delete(nextSeq);
          nextSeq++;
          moved = true;
        }
        if (held.size === 0) gapSince = null;
        else if (moved || gapSince === null) gapSince = now;   // 새 빈 번호를 기다리기 시작
      };

      const onSeq = (seq, sender) => {
        const now = Date.now();
        if (seq < maxSeq && onSeqInversion) onSeqInversion((maxSeqBySender[sender] || 0) > seq);
        maxSeq = Math.max(maxSeq, seq);
        maxSeqBySender[sender] = Math.max(maxSeqBySender[sender] || 0, seq);
        if (nextSeq === null) nextSeq = seq;
        if (seq < nextSeq) { if (onSeqLate) onSeqLate(); return; }   // 이미 건너뛴 번호
        held.set(seq, now);
        releaseInOrder(now);
      };

      socket.setInterval(() => {
        const now = Date.now();
        if (gapSince === null || now - gapSince < SEQ_GAP_WAIT_MS) return;
        if (onSeqGapTimeout) onSeqGapTimeout();
        nextSeq = Math.min(...Array.from(held.keys()));    // 기다리던 번호를 포기하고 다음 있는 번호부터
        releaseInOrder(now);
      }, 50);

      const markRead = (id) => {
        if (!id || id === cursor) return;
        cursor = Math.max(cursor || 0, id);
        if (SEND_VIA === 'stomp') {
          socket.send(frame('SEND', { destination: `/pub/chat-rooms/${chatRoomId}/read`, 'content-type': 'application/json' },
            JSON.stringify({ lastReadMessageId: cursor })));
        } else if (READ_EVERY_SEC > 0 && Date.now() - lastReadAt < READ_EVERY_SEC * 1000) {
          readPending = true;               // 1초 틱에서 묶어 보낸다
        } else {
          readChatRest(token, chatRoomId, cursor);
          lastReadAt = Date.now();
          readPending = false;
        }
      };
      let lastReadAt = 0, readPending = false;

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
            seenIds.add(messageId);
            pendingFromPoll.delete(messageId);
            if (messageId < lastSeenId && onInversion) onInversion();
            lastSeenId = Math.max(lastSeenId, messageId);
            markRead(messageId);
          }
          const seq = data.match(/"seq":(\d+)/);
          if (seq) {
            const sender = data.match(/"content":"lt:\d+:([^"]+)"/);
            onSeq(Number(seq[1]), sender ? sender[1] : 'system');
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
        if (readPending && Date.now() - lastReadAt >= READ_EVERY_SEC * 1000) {
          readChatRest(token, chatRoomId, cursor);
          lastReadAt = Date.now();
          readPending = false;
        }
        if (sinceChatPoll >= (realtimeAlive ? POLL_REALTIME : POLL_BEFORE_REALTIME)) {
          sinceChatPoll = 0;
          const before = cursor;
          const polled = pollChat(token, chatRoomId, cursor);
          if (before && polled.status === 200) {
            for (const m of polled.json('messages')) if (m.id > before && !seenIds.has(m.id)) pendingFromPoll.add(m.id);
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

  if (pendingFromPoll.size > 0 && onPollRecovered) onPollRecovered(pendingFromPoll.size);
  if (!res || res.status !== 101) closedBy = 'handshake';
  return { res, closedBy: closedBy || 'unexpected', errorCode: lastErrorCode, cursor };
}
