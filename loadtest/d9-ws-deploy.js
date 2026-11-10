// D9 - API 배포 중 채팅 WebSocket 이 끊기는지 본다 (소켓 서버 분리 전후 비교, M1·M2)
//   연결 수를 고정하고 DURATION_MIN 동안 붙어 있는다. 도중에 API 를 재배포하고, 그 외에는 아무것도 바꾸지 않는다.
//   끊기면 앱처럼 3초 뒤 다시 붙는다(ChatSocket.kt). 정상 종료(시간 다 됨) 외의 끊김을 전부 센다.
//
//   k6 run -o experimental-prometheus-rw --tag testid=d9-<조건> -e CONNS=150 -e DURATION_MIN=15 --summary-export=results/d9-<조건>.json d9-ws-deploy.js
//     조건 4가지: {분리 전 | 분리 후} × {배포 그룹 AllAtOnce | OneAtATime}
//     분리 전 = dev ALB 에 /ws-chat 규칙 없음(WS 가 API 에 붙음), 분리 후 = /ws-chat* → moyeota-socket-tg
//   시작 3분 뒤 API 재배포를 건다(CodeDeploy 콘솔에서 moyeota-dev 의 직전 배포를 다시 배포). 앞 3분이 평상시 기준이 된다
//   배포 없이 DURATION_MIN=5 로 먼저 돌려 ws_unexpected_close 가 0 인지 확인한다 - 0 이 아니면 측정 전에 원인부터 찾는다
//
//   볼 지표
//     ws_unexpected_close   서버 쪽에서 끊긴 연결 수                      ← M1 핵심
//     ws_handshake_fail     다시 붙으려 했는데 연결 자체가 안 됨(ALB 502·503) ← M2 재연결 폭주
//     ws_error{code}        STOMP ERROR 로 거절됨 - 분리 후 API 가 없는 동안의 재연결은 SERVER_UNAVAILABLE
//     ws_reconnects         끊긴 뒤 다시 붙은 횟수
//     ws_down_ms            끊긴 순간부터 다시 CONNECTED 까지
//     chat_receive_gap_ms   같은 사용자가 메시지를 받는 사이의 공백(재연결을 넘어 이어서 잰다) - max 가 사용자가 느낀 멈춤
//     chat_send_fail        메시지 보내기(REST) 실패
//     chat_poll_recovered   소켓으로 못 받고 폴링으로 뒤늦게 받은 메시지
import { sleep } from 'k6';
import exec from 'k6/execution';
import { Trend, Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { fillRoom, groupUsers, finishRoom, resolveChatRoomId, openChatRoom } from './lib/api.js';
import { chatSession } from './lib/stomp.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));
const CONNS = Number(__ENV.CONNS || 150);                    // 3의 배수 - 3명짜리 방 CONNS/3 개
const DURATION_MIN = Number(__ENV.DURATION_MIN || 15);
const SEND_EVERY_SEC = Number(__ENV.SEND_EVERY_SEC || 12);  // 앱과 같다
const RECONNECT_SEC = 3;                                     // ChatSocket.kt 의 고정 재시도 간격
const END_MARGIN_MS = 5000;                                  // 시나리오 끝나기 이만큼 전에 스스로 닫는다

const connected = new Counter('ws_connected');
const unexpectedClose = new Counter('ws_unexpected_close');
const handshakeFail = new Counter('ws_handshake_fail');
const wsError = new Counter('ws_error');
const reconnects = new Counter('ws_reconnects');
const downMs = new Trend('ws_down_ms', true);
const latency = new Trend('chat_delivery_ms', true);
const receiveGap = new Trend('chat_receive_gap_ms', true);
const sendOk = new Counter('chat_send_ok');
const sendFail = new Counter('chat_send_fail');
const pollRecovered = new Counter('chat_poll_recovered');
const inversions = new Counter('chat_order_inversions');

export const options = {
  scenarios: {
    hold: { executor: 'constant-vus', vus: CONNS, duration: `${DURATION_MIN}m`, gracefulStop: '30s' },
  },
  // 판정용이 아니라 기록용이다. 배포 중 실패는 일부러 만드는 것이라 임계값을 걸지 않는다.
  // 다만 count 를 요약에 남기려고 항상 통과하는 임계값을 건다(k6 는 임계값 없는 태그 지표를 요약에 따로 보여 주지 않는다)
  thresholds: {
    'ws_error{code:SERVER_UNAVAILABLE}': ['count>=0'],
    'ws_error{code:UNKNOWN}': ['count>=0'],
  },
};

export function setup() {
  const rooms = {};
  for (let g = 0; g < CONNS / 3; g++) {
    const partyId = fillRoom(users, g);
    if (!partyId) { console.error(`조 ${g} 방 준비 실패`); continue; }
    rooms[g] = { partyId, chatRoomId: resolveChatRoomId(groupUsers(users, g)[0].token, sleep, 10) };
  }
  return { rooms };
}

// VU 마다 따로 사는 상태 - 반복(iteration)을 넘어 유지된다
let cursor = null;         // 마지막으로 읽은 메시지 id
let opened = false;        // 채팅방을 처음 한 번 열었는지
let downSince = null;      // 끊긴 시각. 다시 CONNECTED 되면 비운다
let lastReceiveAt = null;  // 마지막으로 메시지를 받은 시각

export default function (data) {
  const g = Math.floor((__VU - 1) / 3);
  const room = data.rooms[g];
  if (!room || !room.chatRoomId) { sleep(5); return; }
  const me = users[__VU - 1];

  // 끝날 때 k6 가 끊는 것을 장애로 세지 않기 위해 시나리오가 끝나기 직전까지만 붙어 있는다
  const endAt = exec.scenario.startTime + DURATION_MIN * 60 * 1000 - END_MARGIN_MS;
  const remainingSec = (endAt - Date.now()) / 1000;
  if (remainingSec < 5) { sleep(Math.max(remainingSec, 0) + END_MARGIN_MS / 1000 + 1); return; }

  if (!opened) {
    const o = openChatRoom(me.token, room.chatRoomId);
    if (o.status !== 200) { sleep(RECONNECT_SEC); return; }
    cursor = o.cursor;
    opened = true;
  }

  const result = chatSession(me.token, room.chatRoomId, `vu${__VU}`, {
    holdSec: remainingSec, sendEverySec: SEND_EVERY_SEC, cursor,
    onConnected: () => {
      connected.add(1);
      if (downSince !== null) { downMs.add(Date.now() - downSince); reconnects.add(1); downSince = null; }
    },
    onLatency: (ms) => {
      latency.add(ms);
      const now = Date.now();
      if (lastReceiveAt !== null) receiveGap.add(now - lastReceiveAt);
      lastReceiveAt = now;
    },
    onSend: (ok) => (ok ? sendOk : sendFail).add(1),
    onPollRecovered: (n) => pollRecovered.add(n),
    onInversion: () => inversions.add(1),
  });
  if (result.cursor) cursor = result.cursor;

  switch (result.closedBy) {
    case 'timeout': return;                                    // 정상 - 시나리오 끝
    case 'unexpected': unexpectedClose.add(1); break;
    case 'handshake': handshakeFail.add(1); break;
    case 'error': wsError.add(1, { code: result.errorCode || 'UNKNOWN' }); break;
    default: return;                                           // party-closed 는 이 시나리오에서 생기지 않는다
  }
  if (downSince === null) downSince = Date.now();
  sleep(RECONNECT_SEC);
}

export function teardown(data) {
  for (const g of Object.keys(data.rooms)) finishRoom(groupUsers(users, Number(g))[0].token, data.rooms[g].partyId);
}
