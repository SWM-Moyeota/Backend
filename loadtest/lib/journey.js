// 1차 배포(택시 꺼짐) 사용자 여정 - 앱(frontend develop)의 화면별 호출을 그대로 따른다. D2(전체 여정)와 D6(지속)가 같이 쓴다.
//
//   앱 시작     GET /config · /local/users/info · /users/me/favorite-places · /chat-rooms/me(진행 중인 방 찾기)
//   17 합승탭   GET /matching/rooms?bounds 즉시 + 4초 폴링
//   방 만들기   즐겨찾기 → 경로 미리보기(POST /matching/routes) → POST /matching/rooms      ※ 장소 검색(카카오)은 부하에서 뺀다
//   참여        GET /matching/rooms/{id}(참여 확인 화면) → POST join
//   21 대기     GET /matching/rooms/{id} 즉시 + 4초 폴링. 1차 배포에선 COMPLETED 에서도 계속 돈다(FINISHED 까지)
//              USE_SSE=1 이면 폴링 대신 /events 구독 - changed 신호마다 상세 1회, closed 면 홈으로
//   채팅        채팅 탭 GET /chat-rooms/me → 방 열기 3건 → 소켓 + 폴링(3초→20초) + 읽음
//              그 아래에서  폴링 앱: 21 의 방 상세 4초 폴링이 계속 돈다
//                          SSE 앱 : 방 변화는 SSE 로 받고, 놓친 신호에 대비해 방 상태(/status)만 30초마다 확인한다. 지문이 다르면 그때 상세 1회
//   종료        누군가 POST finish → 나머지는  폴링 앱: 상세 폴링으로 FINISHED 를 본다 / SSE 앱: closed 신호를 받는다
//
// SSE 앱 모델의 한계: k6 는 한 VU 가 WebSocket 과 SSE 를 동시에 붙잡지 못한다. 그래서 채팅하는 동안에는 SSE 연결을 들고 있지 않고
//   (실제 앱은 들고 있다 - 유휴 연결 수는 D7 로 따로 본다), 채팅이 끝난 뒤 SSE 로 closed 를 기다린다(실제 앱에는 없는 재연결 1회).
//
// VU 3개가 한 조: (VU-1)%3 == 0 이 방장. VU 끼리 메모리를 못 나누므로 참여자는 앱처럼 목록을 폴링해 자기 조 방(destination 이 LT-g<조>- 로 시작)을 찾는다.
import { sleep, check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import {
  appStart, listRooms, favoritePlaces, previewRoute, openRoom, joinRoom, leaveRoom, roomDetail, finishRoom,
  myChatRooms, openChatRoom, roomStatus, 판교역, GROUP, spotOf, viewportAround,
} from './api.js';
import { chatSession } from './stomp.js';
import { watchParty, latestJoinedAt, propagationOf as propagation } from './sse.js';

export const USE_SSE = __ENV.USE_SSE === '1';                     // 대기 화면을 폴링 대신 SSE 로 (서버·앱 모두 SSE 배포 후)

export const POLL = Number(__ENV.POLL_SEC || 4);          // 화면 17·21 의 폴링 주기
const STATUS_POLL = Number(__ENV.STATUS_POLL_SEC || 30);  // SSE 앱의 안전망 - 채팅 화면에서 방 상태(/status)를 확인하는 주기
// 비교용: USE_SSE=1 이어도 채팅 중에는 예전처럼 방 상세를 4초마다 폴링한다 (서버 /status 배포 전, 또는 전후 비교)
const LEGACY_DETAIL_POLL = __ENV.LEGACY_DETAIL_POLL === '1';
const LIGHT_WATCH = USE_SSE && !LEGACY_DETAIL_POLL;       // 채팅·종료 구간에서 상세 폴링 대신 SSE + 방 상태를 쓴다
const CHAT_SEC = Number(__ENV.CHAT_SEC || 40);            // 채팅 화면에 머무는 시간
const WAIT_MAX = Number(__ENV.WAIT_MAX_SEC || 90);        // 방이 안 차면 포기하는 시간
const CHAT_TAB_DELAY = Number(__ENV.CHAT_TAB_DELAY_SEC || 2);    // 방이 찬 걸 보고 채팅 탭으로 넘어가기까지 - 사람의 화면 전환 시간
const PROBE_GROUPS = __ENV.PROBE_GROUPS === undefined ? 10 : Number(__ENV.PROBE_GROUPS);   // 앞에서부터 이 수만큼의 조가 탐침을 겸한다 (0 이면 끔)
const PROBE_WAIT = Number(__ENV.PROBE_WAIT_SEC || 60);          // 탐침이 채팅방 입장을 기다려 주는 한도

export const fillTime = new Trend('journey_fill_time_ms', true);        // 방 생성 → 정원 충족
export const chatLatency = new Trend('chat_delivery_ms', true);         // 메시지 전달 지연
export const joinLag = new Trend('chat_join_lag_ms', true);             // 탐침: 방 생성·참여 응답 → 그 방의 채팅방이 내 목록에 뜰 때까지 (비동기 입장 지연)
export const joinLagTimeouts = new Counter('chat_join_lag_timeout');    // 탐침: PROBE_WAIT 안에 끝내 안 뜬 횟수
export const joinOutcome = new Counter('journey_join');                 // result 태그: ok / full / other
export const wsErrors = new Counter('ws_errors');
export const giveUps = new Counter('journey_give_up');                  // 시간 안에 방이 안 차서 포기
export const sweepMinutes = new Trend('abandon_to_finished_min');       // D6: 방치 → 자동 종료까지

// 화면에 그려 둔 방 상세의 지문 (VU 마다 따로 가진다). 방 상태의 지문과 비교해 상세를 다시 읽을지 정한다
let knownFingerprint = null;
const detailOf = (token, partyId) => {
  const res = roomDetail(token, partyId);
  if (res.status === 200) knownFingerprint = res.json('fingerprint') || null;
  return res;
};
const CLOSED = ['FINISHED', 'CANCELED'];

function pollUntil(token, partyId, wanted, maxSec) {
  for (let t = 0; t < maxSec; t += POLL) {
    const res = detailOf(token, partyId);
    if (res.status === 200 && wanted.includes(res.json('status'))) return res.json('status');
    sleep(POLL);
  }
  return null;
}

/** SSE 로 기다린다. changed 마다 상세를 다시 읽어(앱과 같다) 원하는 상태가 되면 끝. 반영 지연도 여기서 잰다 */
function sseUntil(token, partyId, wanted, maxSec) {
  let reached = null;
  let seenMembers = -1;
  const first = detailOf(token, partyId);                                // 앱은 connected 직후 한 번 재조회한다
  if (first.status === 200) { seenMembers = first.json('members').length; if (wanted.includes(first.json('status'))) return first.json('status'); }
  watchParty(token, partyId, {
    holdSec: maxSec,
    onEvent: (name) => {
      if (name !== 'changed' && name !== 'connected') return true;
      const res = detailOf(token, partyId);
      if (res.status !== 200) return true;
      const members = res.json('members').length;
      if (name === 'changed' && members > seenMembers) {                 // 누가 들어왔다 - 그 사람의 joinedAt 기준으로 지연
        const at = latestJoinedAt(res);
        if (at) propagation.add(Date.now() - at);
      }
      seenMembers = members;
      if (wanted.includes(res.json('status'))) { reached = res.json('status'); return false; }
      return true;
    },
  });
  return reached;
}

/**
 * SSE 앱의 종료 대기 - 다른 사람이 합승 완료를 누르면 closed 신호가 온다. 상세를 읽지 않고 방 상태만 본다.
 * 붙기 전에 이미 닫혔을 수 있어 먼저 한 번, 붙은 직후(connected) 한 번 확인한다 - 그 사이에 난 closed 는 신호로 오지 않는다.
 */
function sseUntilClosed(token, partyId, maxSec) {
  const closed = () => { const st = roomStatus(token, partyId); return st.status === 200 && CLOSED.includes(st.json('status')); };
  if (closed()) return true;
  let seen = false;
  const { closedBy } = watchParty(token, partyId, {
    holdSec: maxSec,
    onEvent: (name) => { if (name === 'connected' && closed()) { seen = true; return false; } return true; },
  });
  return seen || closedBy === 'closed';
}
const waitUntil = (token, partyId, wanted, maxSec) => (USE_SSE ? sseUntil(token, partyId, wanted, maxSec) : pollUntil(token, partyId, wanted, maxSec));

/**
 * 탐침 - 방 생성·참여가 커밋된 뒤 채팅방 입장(비동기 리스너)이 끝나기까지 얼마나 걸리는지 잰다.
 *
 * 나머지 수천 명은 앱처럼 /chat-rooms/me 를 한 번만 부른다. 전원이 확인하려고 두드리면 확인 자체가 부하가 되어
 * 재려는 것을 바꿔 버린다. 그래서 측정은 PROBE_GROUPS 개 조(기본 10조 = 30명)만 따로 한다 - 5,000명 중 0.6% 라
 * 부하에는 영향이 없고 분포를 보기엔 충분하다. 호출에는 (probe) 이름표를 붙여 앱이 만드는 호출과 지표에서 갈라 둔다.
 *
 * 간격은 0.1초에서 두 배씩 늘려 2초에서 멈춘다 - 정상일 때(수십 ms)는 촘촘히, 밀릴 때는 가볍게.
 * 끝내 안 뜨면 한도 값을 그대로 기록한다 - 빼 버리면 가장 나쁜 표본이 사라져 p95 가 좋아 보인다.
 */
function probeJoinLag(token, destName, since) {
  for (let gap = 0.1; ; gap = Math.min(gap * 2, 2)) {
    const rooms = myChatRooms(token, 'GET /chat-rooms/me (probe)');
    if (rooms.status === 200 && rooms.json().some((r) => r.destination === destName)) { joinLag.add(Date.now() - since); return; }
    if ((Date.now() - since) / 1000 + gap > PROBE_WAIT) { joinLag.add(PROBE_WAIT * 1000); joinLagTimeouts.add(1); return; }
    sleep(gap);
  }
}

/**
 * 채팅 탭으로 들어가 방을 연다. 돌려주는 값은 소켓이 닫힌 이유.
 * 목록의 첫 항목이 아니라 "이번 방"의 채팅방을 destName 으로 찾는다 - 입장이 늦으면 첫 항목은 지난 반복의 옛 방이고,
 * 거기에 붙으면 입장 체크가 거짓으로 통과하고 몇 분 전 메시지가 전달 지연으로 잡힌다.
 */
function chat(user, who, partyId, destName) {
  // 앱은 채팅 탭에 들어갈 때 /chat-rooms/me 를 한 번 부른다. 방이 안 보이면 사용자는 "방이 없다"를 본다 - 그게 실제 장애다.
  // 그래서 여기서도 한 번만 부르고, 없으면 실패로 남긴 채 채팅을 건너뛴다. 다시 두드려 가며 기다리면
  //   · 늦게라도 생기면 통과해 문제가 가려지고
  //   · 입장이 밀릴수록 이 무거운 조회(쿼리 5개)가 늘어 DB 를 더 누른다(1초 x 10번일 때 5,000명에서 초당 720건, 전체의 40%).
  // 채팅방 입장은 커밋 직후 비동기로 처리된다. 마지막 참여자는 join 과 동시에 방이 차서 곧바로 여기로 오므로,
  // 사람이 화면을 넘기는 시간만큼은 둔다 - 이게 없으면 정상인 서버에서도 수십 ms 차이로 거짓 실패가 난다.
  sleep(CHAT_TAB_DELAY);
  const rooms = myChatRooms(user.token);
  const mine = rooms.status === 200 ? rooms.json().find((r) => r.destination === destName) : null;
  const chatRoomId = mine ? mine.chatRoomId : null;
  if (!check(chatRoomId, { '채팅방에 입장돼 있다': (id) => id !== null })) {
    sleep(CHAT_SEC);              // 채팅을 못 해도 합승 시간은 같다 - 바로 끝내면 실패한 조만 빨리 돌아 방 생성이 부풀려진다
    return 'no-room';
  }

  const opened = openChatRoom(user.token, chatRoomId);
  check(opened, { '채팅방 열기 200': (o) => o.status === 200 });
  const { closedBy } = chatSession(user.token, chatRoomId, who, {
    holdSec: CHAT_SEC, sendEverySec: 12, cursor: opened.cursor, partyId,
    statusPollSec: LIGHT_WATCH ? STATUS_POLL : null, fingerprint: knownFingerprint,
    onLatency: (ms) => chatLatency.add(ms),
    onError: (e) => { wsErrors.add(1); console.error(`WS ${who}: ${e}`); },
  });
  return closedBy;
}

/** abandon=true 면 아무도 「합승 완료」를 누르지 않는다(D6) - 30분 뒤 스윕이 닫아야 한다 */
export function journey(users, vu, { abandon = false } = {}) {
  const g = Math.floor((vu - 1) / GROUP);
  const role = (vu - 1) % GROUP;
  const me = users[vu - 1];
  const prefix = `LT-g${g}-`;
  const from = spotOf(g);                                                 // 같은 조는 같은 출발지에서 만난다
  const probe = g < PROBE_GROUPS;                                         // 이 조는 여정을 그대로 돌면서 입장 지연도 잰다
  let destName = null;                                                    // 이번 방의 이름 - 채팅방을 찾는 열쇠

  check(appStart(me.token), { '1차 배포 모드(taxiEnabled=false)': (r) => r.status === 200 && r.json('taxiEnabled') === false });

  let partyId = null;
  if (role === 0) {
    favoritePlaces(me.token);                                             // 15 목적지 입력
    sleep(2);
    check(previewRoute(me.token, from), { '경로 미리보기 200': (r) => r.status === 200 });   // 16 목적지 확인
    sleep(2);
    const opened = Date.now();
    destName = `${prefix}${opened}`;
    const r = openRoom(me.token, 3, from.dest, destName, from);
    if (!check(r, { '방 생성 200': (x) => x.status === 200 })) { console.error(`open ${r.status}: ${r.body}`); sleep(POLL); return; }
    partyId = r.id;
    if (probe) probeJoinLag(me.token, destName, Date.now());
    if (!waitUntil(me.token, partyId, ['COMPLETED'], WAIT_MAX)) { giveUps.add(1); leaveRoom(me.token, partyId); return; }
    fillTime.add(Date.now() - opened);
  } else {
    sleep(role);                                                          // 두 참여자가 같은 순간에 몰리지 않게
    for (let t = 0; t < WAIT_MAX && partyId === null; t += POLL) {        // 17 합승 탭 - 4초 폴링
      const res = listRooms(me.token, viewportAround(from));              // 내 주변만 본다
      const room = res.status === 200 ? res.json('list').find((p) => p.destination.startsWith(prefix)) : null;
      if (room) {
        destName = room.destination;
        roomDetail(me.token, room.partyId);                               // 18 참여 확인 화면이 상세를 먼저 읽는다
        sleep(1);
        const j = joinRoom(me.token, room.partyId);
        if (j.status === 200) { joinOutcome.add(1, { result: 'ok' }); partyId = room.partyId; if (probe) probeJoinLag(me.token, destName, Date.now()); break; }
        if (j.status === 409) joinOutcome.add(1, { result: 'full' });
        else { joinOutcome.add(1, { result: 'other' }); console.error(`join ${j.status}: ${j.body}`); }
      }
      sleep(POLL);
    }
    if (partyId === null) { giveUps.add(1); return; }
    if (!waitUntil(me.token, partyId, ['COMPLETED'], WAIT_MAX)) { giveUps.add(1); leaveRoom(me.token, partyId); return; }
  }

  const closedBy = chat(me, `g${g}r${role}`, partyId, destName);                    // 채팅하는 동안에도 21 의 상세 폴링이 같이 돈다

  if (abandon) {                                                          // D6 - 아무도 닫지 않는다
    const left = Date.now();
    const status = pollUntil(me.token, partyId, ['FINISHED'], 45 * 60);
    if (check(status, { '방치된 방이 자동 종료된다': (s) => s === 'FINISHED' })) sweepMinutes.add((Date.now() - left) / 60000);
    if (role === 0) {                                                     // 풀려났으면 새 방을 만들 수 있어야 한다
      const again = openRoom(me.token, 3, from.dest, `${prefix}again`, from);
      check(again, { '자동 종료 뒤 새 방을 만들 수 있다': (x) => x.status === 200 });
      if (again.id) leaveRoom(me.token, again.id);
    }
    return;
  }

  if (closedBy !== 'party-closed') {
    if (role === 0) check(finishRoom(me.token, partyId), { '합승 완료 204 (또는 이미 닫힘 409)': (r) => r.status === 204 || r.status === 409 });
    else if (LIGHT_WATCH) sseUntilClosed(me.token, partyId, 60);
    else pollUntil(me.token, partyId, CLOSED, 60);
  }
  myChatRooms(me.token); favoritePlaces(me.token);                        // 홈으로 돌아오면 진행 중인 방을 다시 찾는다
  sleep(POLL);
}
