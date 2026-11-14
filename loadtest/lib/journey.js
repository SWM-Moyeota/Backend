// 사용자 여정 - 앱의 화면별 호출을 그대로 따른다. D2(전체 여정)와 D6(지속)가 같이 쓴다.
//
//   앱 시작     GET /config · /local/users/info · /users/me/favorite-places · /chat-rooms/me(진행 중인 방 찾기)
//   17 합승탭   GET /matching/rooms?bounds 즉시 + 4초 폴링
//   방 만들기   즐겨찾기 → 경로 미리보기(POST /matching/routes) → POST /matching/rooms      ※ 장소 검색(카카오)은 부하에서 뺀다
//   참여        GET /matching/rooms/{id}(참여 확인 화면) → POST join
//   21 대기     /events 구독 - connected(방 상세 전체)로 그리고 변경분(member-joined·member-left·confirmed·member-arrived)을
//              version 순서대로 반영한다(번호가 빠지면 상세 재조회). 방 상세 폴링은 없다
//              정원이 차도 방은 ACTIVE 그대로다 - 방장은 멤버 수로 찼는지 알고, 참여자는 confirmed 를 기다린다
//   약속 확정   방장: 방이 차면 POST confirm(약속 시간 = 지금 + CONFIRM_AHEAD_SEC, 만남 장소 = 출발지) → 방이 COMPLETED
//   채팅        채팅 탭 GET /chat-rooms/me → 방 열기 3건 → 소켓 + 폴링(3초→20초) + 읽음
//   도착        전원: POST arrive(출발지 좌표). 약속 10분 전부터 되므로 확정 직후에도 된다
//   출발        방장: 약속 시간이 되면 POST depart → 방이 FINISHED. 나머지는 SSE closed 를 받는다
//
// 서버가 약속 시간을 "지금부터 5분 뒤" 부터만 받으므로 한 바퀴가 5분 30초쯤 걸린다 - 유지 구간을 그보다 넉넉히 잡아야 방이 끝까지 간다.
//
// k6 는 한 VU 가 WebSocket 과 SSE 를 동시에 붙잡지 못한다. 그래서 채팅하는 동안에는 SSE 연결을 들고 있지 않고
//   (실제 앱은 들고 있다 - 유휴 연결 수는 D7 로 따로 본다), 채팅이 끝난 뒤 다시 붙어 connected 로 화면을 새로 그린다(실제 앱에는 없는 재연결 1회).
// SSE 가 에러로 끊기면 앱처럼 3초 뒤 다시 붙는다(sse_reconnects). version 이 어긋나면 앱처럼 상세를 다시 읽는다(sse_resync, (resync) 이름표).
// 방이 찬 직후(방장)와 확정을 본 직후(참여자) 화면을 방 상세와 1회 맞춘다(sse_view_check, (check) 이름표).
//
// VU 3개가 한 조: (VU-1)%3 == 0 이 방장. VU 끼리 메모리를 못 나누므로 참여자는 앱처럼 목록을 폴링해 자기 조 방(destination 이 LT-g<조>- 로 시작)을 찾는다.
import { sleep, check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import {
  appStart, listRooms, favoritePlaces, previewRoute, openRoom, joinRoom, leaveRoom, roomDetail, confirmRoom, arriveRoom, departRoom,
  myChatRooms, openChatRoom, GROUP, spotOf, viewportAround,
} from './api.js';
import { chatSession } from './stomp.js';
import { watchParty, newRoomView, follow, isFull, compareWithDetail, sseReconnects } from './sse.js';

export const POLL = Number(__ENV.POLL_SEC || 4);          // 화면 17(합승 탭)의 목록 폴링 주기
const CHAT_SEC = Number(__ENV.CHAT_SEC || 40);            // 채팅 화면에 머무는 시간
const WAIT_MAX = Number(__ENV.WAIT_MAX_SEC || 90);        // 방이 안 차면(참여자는 확정이 안 오면) 포기하는 시간
const CHAT_TAB_DELAY = Number(__ENV.CHAT_TAB_DELAY_SEC || 2);    // 확정을 보고 채팅 탭으로 넘어가기까지 - 사람의 화면 전환 시간
const PROBE_GROUPS = __ENV.PROBE_GROUPS === undefined ? 10 : Number(__ENV.PROBE_GROUPS);   // 앞에서부터 이 수만큼의 조가 탐침을 겸한다 (0 이면 끔)
const PROBE_WAIT = Number(__ENV.PROBE_WAIT_SEC || 60);          // 탐침이 채팅방 입장을 기다려 주는 한도
const RECONNECT_SEC = 3;                                         // 앱(PartyEventSource)의 재연결 간격
const CLOSE_WAIT = 60;                                           // 약속 시간 뒤 방장의 출발(closed)을 기다려 주는 한도
const DEPART_RETRY_SEC = 2;                                      // 서버 시계가 조금 늦어 "약속 시간 전" 으로 거절되면 다시 누르는 간격
const CHECK = 'GET /matching/rooms/{id} (check)';                // 측정용 상세 조회 - 앱이 만드는 호출과 지표에서 가른다

export const fillTime = new Trend('journey_fill_time_ms', true);        // 방 생성 → 정원 충족
export const chatLatency = new Trend('chat_delivery_ms', true);         // 메시지 전달 지연
export const joinLag = new Trend('chat_join_lag_ms', true);             // 탐침: 방 생성·참여 응답 → 그 방의 채팅방이 내 목록에 뜰 때까지 (비동기 입장 지연)
export const joinLagTimeouts = new Counter('chat_join_lag_timeout');    // 탐침: PROBE_WAIT 안에 끝내 안 뜬 횟수
export const joinOutcome = new Counter('journey_join');                 // result 태그: ok / full / other
export const wsErrors = new Counter('ws_errors');
export const giveUps = new Counter('journey_give_up');                  // cause 태그: 아래 GIVE_UP_CAUSES
// not-full 방이 안 찼다 / not-confirmed 방장이 확정하지 않았다 / missed-event 방은 바뀌었는데 SSE 로 못 받았다 / no-room 합승 탭에서 방을 못 찾았다 / unknown 상세 조회 실패
export const GIVE_UP_CAUSES = ['not-full', 'not-confirmed', 'missed-event', 'no-room', 'unknown'];
export const finished = new Counter('journey_finished');                // result 태그: departed(방장이 출발) / closed(참여자가 closed 수신) / timeout(끝을 못 봄)
export const FINISH_RESULTS = ['departed', 'closed', 'timeout'];
export const orderInversions = new Counter('chat_order_inversions');    // 소켓으로 받은 메시지 id 가 직전보다 작았던 횟수

const CLOSED = ['FINISHED', 'CANCELED'];

/**
 * maxSec 안에서 방 변화를 듣다가 done() 이 참이면 끝. 에러로 끊기면 앱처럼 RECONNECT_SEC 뒤 다시 붙는다 -
 * 새 connected 는 version 이 화면 이상이면 view 를 통째로 바꾼다. 403·404 는 다시 붙어도 같은 답이라 멈춘다(앱도 같다). 돌려주는 값은 마지막 연결의 HTTP 상태
 */
function sseWatch(token, partyId, view, done, maxSec) {
  const until = Date.now() + maxSec * 1000;
  for (;;) {
    const r = watchParty(token, partyId, {
      holdSec: Math.max(1, (until - Date.now()) / 1000),
      onEvent: (name, data) => { follow(view, token, partyId, name, data); return !done(); },
    });
    if (r.closedBy !== 'error' || r.status === 403 || r.status === 404 || done()) return r.status;
    if ((until - Date.now()) / 1000 <= RECONNECT_SEC) return r.status;
    sleep(RECONNECT_SEC);
    sseReconnects.add(1);
  }
}

/**
 * SSE 로 기다린다. connected 로 방을 그리고 변경분을 version 순서대로 반영해 reached(view) 가 참이 되면 끝 - 상세는 앱처럼 version 이 어긋날 때만 다시 읽는다.
 * 닿았으면 그때 한 번만 상세와 맞춰 본다(측정용). 돌려주는 값은 화면(view), 못 닿았으면 null
 */
function sseUntil(token, partyId, reached, maxSec) {
  const view = newRoomView();
  sseWatch(token, partyId, view, () => reached(view), maxSec);
  if (!reached(view)) return null;
  compareWithDetail(view, roomDetail(token, partyId, CHECK));
  return view;
}

const isConfirmed = (view) => view.status === 'COMPLETED';

/**
 * 방이 닫히기를 기다린다 - 다시 붙어 connected 의 상태가 이미 FINISHED·CANCELED 면 끝, 아니면 closed 를 기다린다.
 * 모두 나가 CANCELED 된 방은 멤버가 아니라 구독이 403 으로 거절된다 - 그것도 닫힘으로 본다.
 */
function sseUntilClosed(token, partyId, maxSec) {
  const view = newRoomView();
  const status = sseWatch(token, partyId, view, () => view.closed || CLOSED.includes(view.status), maxSec);
  return view.closed || CLOSED.includes(view.status) || status === 403;
}

/**
 * 대기 포기 - 방을 나가고 이유를 cause 로 남긴다. 상세를 1회 읽어 가른다:
 *   기다리던 변화가 상세에는 이미 있으면 missed-event(방은 바뀌었는데 신호를 못 받았다), 아니면 정말 안 일어난 것(방장 not-full, 참여자 not-confirmed).
 */
function giveUp(token, partyId, waitingFor) {
  const d = roomDetail(token, partyId, CHECK);
  let cause = 'unknown';
  if (d.status === 200) {
    const happened = waitingFor === 'full' ? d.json('members').length >= d.json('capacity') : d.json('status') === 'COMPLETED';
    cause = happened ? 'missed-event' : waitingFor === 'full' ? 'not-full' : 'not-confirmed';
  }
  giveUps.add(1, { cause });
  leaveRoom(token, partyId);
}

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
function chat(user, who, destName) {
  // 앱은 채팅 탭에 들어갈 때 /chat-rooms/me 를 한 번 부른다. 방이 안 보이면 사용자는 "방이 없다"를 본다 - 그게 실제 장애다.
  // 그래서 여기서도 한 번만 부르고, 없으면 실패로 남긴 채 채팅을 건너뛴다. 다시 두드려 가며 기다리면
  //   · 늦게라도 생기면 통과해 문제가 가려지고
  //   · 입장이 밀릴수록 이 무거운 조회(쿼리 5개)가 늘어 DB 를 더 누른다(1초 x 10번일 때 5,000명에서 초당 720건, 전체의 40%).
  // 채팅방 입장은 커밋 직후 비동기로 처리된다. 방장이 방이 차자마자 확정하면 마지막 참여자는 join 직후 곧바로 여기로 오므로,
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
    holdSec: CHAT_SEC, sendEverySec: 12, cursor: opened.cursor,
    onLatency: (ms) => chatLatency.add(ms),
    onInversion: () => orderInversions.add(1),
    onError: (e) => { wsErrors.add(1); console.error(`WS ${who}: ${e}`); },
  });
  return closedBy;
}

/**
 * 방장의 출발 - 약속 시간까지 대기 화면(SSE)에 머물다가 출발을 누른다. 그동안 다른 멤버의 도착(member-arrived)을 받는다.
 * 서버 시계가 조금 늦으면 MEETING_NOT_STARTED(409)로 거절되므로 몇 번 다시 누른다. 방이 먼저 닫혔으면(모두 나감) 누르지 않는다
 */
function departAt(token, partyId, departureAt) {
  const view = newRoomView();
  const waitSec = (departureAt - Date.now()) / 1000;
  if (waitSec > 0) sseWatch(token, partyId, view, () => view.closed || CLOSED.includes(view.status), waitSec);
  if (view.closed || CLOSED.includes(view.status)) return false;
  for (let n = 0; n < 5; n++) {
    const res = departRoom(token, partyId);
    if (res.status === 204) return true;
    if (!(res.status === 409 && String(res.body).includes('MEETING_NOT_STARTED'))) { console.error(`depart ${res.status}: ${res.body}`); return false; }
    sleep(DEPART_RETRY_SEC);
  }
  return false;
}

export function journey(users, vu) {
  const g = Math.floor((vu - 1) / GROUP);
  const role = (vu - 1) % GROUP;
  const me = users[vu - 1];
  const prefix = `LT-g${g}-`;
  const from = spotOf(g);                                                 // 같은 조는 같은 출발지에서 만난다
  const probe = g < PROBE_GROUPS;                                         // 이 조는 여정을 그대로 돌면서 입장 지연도 잰다
  let destName = null;                                                    // 이번 방의 이름 - 채팅방을 찾는 열쇠
  let departureAt = null;                                                 // 약속 시각(ms)

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
    if (!sseUntil(me.token, partyId, isFull, WAIT_MAX)) { giveUp(me.token, partyId, 'full'); return; }   // 21 대기 - 정원이 찰 때까지
    fillTime.add(Date.now() - opened);
    const c = confirmRoom(me.token, partyId, from);                       // 약속 확정 - 방이 COMPLETED 가 되고 참여자에게 confirmed 가 간다
    if (!check(c, { '약속 확정 204': (x) => x.status === 204 })) { console.error(`confirm ${c.status}: ${c.body}`); leaveRoom(me.token, partyId); return; }
    departureAt = c.departureAt;
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
    if (partyId === null) { giveUps.add(1, { cause: 'no-room' }); return; }   // 합승 탭에서 자기 조 방을 못 찾았다(또는 참여 실패)
    const view = sseUntil(me.token, partyId, isConfirmed, WAIT_MAX);          // 21 대기 - 방장의 확정을 기다린다
    if (!view) { giveUp(me.token, partyId, 'confirmed'); return; }
    departureAt = Date.parse(view.departureAt);
  }

  chat(me, `g${g}r${role}`, destName);

  check(arriveRoom(me.token, partyId, from), { '출발지 도착 204': (r) => r.status === 204 });

  if (role === 0) {
    finished.add(1, { result: departAt(me.token, partyId, departureAt) ? 'departed' : 'timeout' });
  } else {
    const waitSec = Math.max(0, (departureAt - Date.now()) / 1000) + CLOSE_WAIT;
    finished.add(1, { result: sseUntilClosed(me.token, partyId, waitSec) ? 'closed' : 'timeout' });
  }
  myChatRooms(me.token); favoritePlaces(me.token);                        // 홈으로 돌아오면 진행 중인 방을 다시 찾는다
  sleep(POLL);
}
