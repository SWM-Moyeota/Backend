// k6/ws 위에 얹은 최소 STOMP 1.2 클라이언트 - CONNECT / SUBSCRIBE / SEND / heartbeat 만 다룬다
import ws from 'k6/ws';
import { BASE } from './api.js';

const NUL = String.fromCharCode(0);   // STOMP 프레임 종료 문자

export function wsUrl() {
  return `${BASE.replace(/^http/, 'ws')}/ws-chat`;
}

function frame(command, headers, body = '') {
  const head = Object.keys(headers).map((k) => `${k}:${headers[k]}`).join('\n');
  return `${command}\n${head}\n\n${body}${NUL}`;
}

/**
 * 채팅방 하나에 붙어 holdSec 동안 머문다. sendEverySec 마다 메시지를 보내고, 받은 메시지의 전달 지연을 onLatency 로 넘긴다.
 * 본문은 "lt:<보낸 시각 ms>:<보낸 사람>" - 보내는 쪽과 받는 쪽이 같은 장비(k6)라 시계가 같다.
 * sendEverySec = 0 이면 듣기만 한다.
 */
export function chatSession(token, chatRoomId, who, { holdSec = 30, sendEverySec = 15, onLatency, onError, onConnected } = {}) {
  return ws.connect(wsUrl(), { tags: { name: 'WS /ws-chat' } }, (socket) => {
    socket.on('open', () => {
      socket.send(frame('CONNECT', { 'accept-version': '1.2', host: 'moyeota', Authorization: `Bearer ${token}`, 'heart-beat': '10000,10000' }));
    });

    socket.on('message', (data) => {
      if (data === '\n' || data === '') return;                       // 서버 heartbeat
      const command = data.substring(0, data.indexOf('\n'));
      if (command === 'CONNECTED') {
        if (onConnected) onConnected();
        socket.send(frame('SUBSCRIBE', { id: 'sub-0', destination: `/sub/chat-rooms/${chatRoomId}` }));
        socket.send(frame('SUBSCRIBE', { id: 'sub-err', destination: '/user/queue/errors' }));
        socket.setInterval(() => socket.send('\n'), 10000);          // 클라이언트 heartbeat - 안 보내면 서버가 끊는다
        if (sendEverySec > 0) {
          const send = () => socket.send(frame('SEND',
            { destination: `/pub/chat-rooms/${chatRoomId}/messages`, 'content-type': 'application/json' },
            JSON.stringify({ content: `lt:${Date.now()}:${who}` })));
          socket.setTimeout(send, 1000 + Math.random() * 2000);       // 첫 메시지가 접속 직후 몰리지 않게 흩뜨린다
          socket.setInterval(send, sendEverySec * 1000);
        }
      } else if (command === 'MESSAGE') {
        const m = data.match(/"content":"lt:(\d+):/);
        if (m && onLatency) onLatency(Date.now() - Number(m[1]));
      } else if (command === 'ERROR') {
        if (onError) onError(data.substring(0, 300));
        socket.close();
      }
    });

    socket.on('error', (e) => { if (onError) onError(String(e.error ? e.error() : e)); });
    socket.setTimeout(() => socket.close(), holdSec * 1000);
  });
}
