// 1차 배포(택시 꺼짐) 사용자 여정 - 앱(frontend develop)의 화면별 호출을 그대로 따른다. D2(전체 여정)와 D6(지속)가 같이 쓴다.
//
//   앱 시작     GET /config · /local/users/info · /users/me/favorite-places · /chat-rooms/me(진행 중인 방 찾기)
//   17 합승탭   GET /matching/rooms?bounds 즉시 + 4초 폴링
//   방 만들기   즐겨찾기 → 경로 미리보기(POST /matching/routes) → POST /matching/rooms      ※ 장소 검색(카카오)은 부하에서 뺀다
//   참여        GET /matching/rooms/{id}(참여 확인 화면) → POST join
//   21 대기     GET /matching/rooms/{id} 즉시 + 4초 폴링. 1차 배포에선 COMPLETED 에서도 계속 돈다(FINISHED 까지)
//              USE_SSE=1 이면 폴링 대신 /events 구독 - changed 신호마다 상세 1회, closed 면 홈으로
//   채팅        채팅 탭 GET /chat-rooms/me → 방 열기 3건 → 소켓 + 폴링(3초→20초) + 읽음. 그 아래에서 21 의 4초 폴링이 계속 돈다
//   종료        누군가 POST finish → 나머지는 상세 폴링으로 FINISHED 를 보고 홈으로
//
// VU 3개가 한 조: (VU-1)%3 == 0 이 방장. VU 끼리 메모리를 못 나누므로 참여자는 앱처럼 목록을 폴링해 자기 조 방(destination 이 LT-g<조>- 로 시작)을 찾는다.
import { sleep, check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import {
  appStart, listRooms, favoritePlaces, previewRoute, openRoom, joinRoom, leaveRoom, roomDetail, finishRoom,
  myChatRooms, openChatRoom, 판교역, GROUP,
} from './api.js';
import { chatSession } from './stomp.js';
import { watchParty, latestJoinedAt, propagationOf as propagation } from './sse.js';

export const USE_SSE = __ENV.USE_SSE === '1';                     // 대기 화면을 폴링 대신 SSE 로 (서버·앱 모두 SSE 배포 후)

export const POLL = Number(__ENV.POLL_SEC || 4);          // 화면 17·21 의 폴링 주기
const CHAT_SEC = Number(__ENV.CHAT_SEC || 40);            // 채팅 화면에 머무는 시간
const WAIT_MAX = Number(__ENV.WAIT_MAX_SEC || 90);        // 방이 안 차면 포기하는 시간

export const fillTime = new Trend('journey_fill_time_ms', true);        // 방 생성 → 정원 충족
export const chatLatency = new Trend('chat_delivery_ms', true);         // 메시지 전달 지연
export const joinOutcome = new Counter('journey_join');                 // result 태그: ok / full / other
export const wsErrors = new Counter('ws_errors');
export const giveUps = new Counter('journey_give_up');                  // 시간 안에 방이 안 차서 포기
export const sweepMinutes = new Trend('abandon_to_finished_min');       // D6: 방치 → 자동 종료까지

function pollUntil(token, partyId, wanted, maxSec) {
  for (let t = 0; t < maxSec; t += POLL) {
    const res = roomDetail(token, partyId);
    if (res.status === 200 && wanted.includes(res.json('status'))) return res.json('status');
    sleep(POLL);
  }
  return null;
}

/** SSE 로 기다린다. changed 마다 상세를 다시 읽어(앱과 같다) 원하는 상태가 되면 끝. 반영 지연도 여기서 잰다 */
function sseUntil(token, partyId, wanted, maxSec) {
  let reached = null;
  let seenMembers = -1;
  const first = roomDetail(token, partyId);                              // 앱은 connected 직후 한 번 재조회한다
  if (first.status === 200) { seenMembers = first.json('members').length; if (wanted.includes(first.json('status'))) return first.json('status'); }
  watchParty(token, partyId, {
    holdSec: maxSec,
    onEvent: (name) => {
      if (name !== 'changed' && name !== 'connected') return true;
      const res = roomDetail(token, partyId);
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

const waitUntil = (token, partyId, wanted, maxSec) => (USE_SSE ? sseUntil(token, partyId, wanted, maxSec) : pollUntil(token, partyId, wanted, maxSec));

/** 채팅 탭으로 들어가 방을 연다. 돌려주는 값은 소켓이 닫힌 이유 */
function chat(user, who, partyId) {
  let chatRoomId = null;
  for (let i = 0; i < 10 && chatRoomId === null; i++) {                  // 채팅방 입장은 비동기라 목록에 늦게 뜰 수 있다
    const rooms = myChatRooms(user.token);
    if (rooms.status === 200 && rooms.json().length > 0) chatRoomId = rooms.json()[0].chatRoomId;
    else sleep(1);
  }
  if (!check(chatRoomId, { '채팅방에 입장돼 있다': (id) => id !== null })) return 'no-room';

  const opened = openChatRoom(user.token, chatRoomId);
  check(opened, { '채팅방 열기 200': (o) => o.status === 200 });
  const { closedBy } = chatSession(user.token, chatRoomId, who, {
    holdSec: CHAT_SEC, sendEverySec: 12, cursor: opened.cursor, partyId,
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

  check(appStart(me.token), { '1차 배포 모드(taxiEnabled=false)': (r) => r.status === 200 && r.json('taxiEnabled') === false });

  let partyId = null;
  if (role === 0) {
    favoritePlaces(me.token);                                             // 15 목적지 입력
    sleep(2);
    check(previewRoute(me.token), { '경로 미리보기 200': (r) => r.status === 200 });   // 16 목적지 확인
    sleep(2);
    const opened = Date.now();
    const r = openRoom(me.token, 3, 판교역, `${prefix}${opened}`);
    if (!check(r, { '방 생성 200': (x) => x.status === 200 })) { console.error(`open ${r.status}: ${r.body}`); sleep(POLL); return; }
    partyId = r.id;
    if (!waitUntil(me.token, partyId, ['COMPLETED'], WAIT_MAX)) { giveUps.add(1); leaveRoom(me.token, partyId); return; }
    fillTime.add(Date.now() - opened);
  } else {
    sleep(role);                                                          // 두 참여자가 같은 순간에 몰리지 않게
    for (let t = 0; t < WAIT_MAX && partyId === null; t += POLL) {        // 17 합승 탭 - 4초 폴링
      const res = listRooms(me.token);
      const room = res.status === 200 ? res.json('list').find((p) => p.destination.startsWith(prefix)) : null;
      if (room) {
        roomDetail(me.token, room.partyId);                               // 18 참여 확인 화면이 상세를 먼저 읽는다
        sleep(1);
        const j = joinRoom(me.token, room.partyId);
        if (j.status === 200) { joinOutcome.add(1, { result: 'ok' }); partyId = room.partyId; break; }
        if (j.status === 409) joinOutcome.add(1, { result: 'full' });
        else { joinOutcome.add(1, { result: 'other' }); console.error(`join ${j.status}: ${j.body}`); }
      }
      sleep(POLL);
    }
    if (partyId === null) { giveUps.add(1); return; }
    if (!waitUntil(me.token, partyId, ['COMPLETED'], WAIT_MAX)) { giveUps.add(1); leaveRoom(me.token, partyId); return; }
  }

  const closedBy = chat(me, `g${g}r${role}`, partyId);                    // 채팅하는 동안에도 21 의 상세 폴링이 같이 돈다

  if (abandon) {                                                          // D6 - 아무도 닫지 않는다
    const left = Date.now();
    const status = pollUntil(me.token, partyId, ['FINISHED'], 45 * 60);
    if (check(status, { '방치된 방이 자동 종료된다': (s) => s === 'FINISHED' })) sweepMinutes.add((Date.now() - left) / 60000);
    if (role === 0) {                                                     // 풀려났으면 새 방을 만들 수 있어야 한다
      const again = openRoom(me.token, 3, 판교역, `${prefix}again`);
      check(again, { '자동 종료 뒤 새 방을 만들 수 있다': (x) => x.status === 200 });
      if (again.id) leaveRoom(me.token, again.id);
    }
    return;
  }

  if (closedBy !== 'party-closed') {
    if (role === 0) check(finishRoom(me.token, partyId), { '합승 완료 204 (또는 이미 닫힘 409)': (r) => r.status === 204 || r.status === 409 });
    else pollUntil(me.token, partyId, ['FINISHED', 'CANCELED'], 60);
  }
  myChatRooms(me.token); favoritePlaces(me.token);                        // 홈으로 돌아오면 진행 중인 방을 다시 찾는다
  sleep(POLL);
}
