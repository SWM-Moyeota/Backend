// 1차 배포(택시 꺼짐) 사용자 여정 - D2(전체 여정)와 D6(지속)가 같이 쓴다.
// VU 3개가 한 조: (VU-1)%3 == 0 이 방장, 나머지 둘이 참여자. VU 끼리는 메모리를 못 나누므로
// 참여자는 실제 앱처럼 "지도 목록을 폴링해서" 자기 조 방(destination 이 LT-g<조>- 로 시작)을 찾는다.
import { sleep, check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import {
  appConfig, listRooms, openRoom, joinRoom, leaveRoom, roomDetail, finishRoom,
  resolveChatRoomId, chatMessages, 판교역, GROUP,
} from './api.js';
import { chatSession } from './stomp.js';

export const POLL = Number(__ENV.POLL_SEC || 4);          // 화면 17·21 의 폴링 주기
const CHAT_SEC = Number(__ENV.CHAT_SEC || 30);            // 채팅 화면에 머무는 시간
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

function chat(user, who) {
  const chatRoomId = resolveChatRoomId(user.token, sleep);
  if (!check(chatRoomId, { '채팅방에 입장돼 있다': (id) => id !== null })) return;
  check(chatMessages(user.token, chatRoomId), { '채팅 기록 200': (r) => r.status === 200 });
  chatSession(user.token, chatRoomId, who, {
    holdSec: CHAT_SEC, sendEverySec: 12,
    onLatency: (ms) => chatLatency.add(ms),
    onError: (e) => { wsErrors.add(1); console.error(`WS ${who}: ${e}`); },
  });
}

/** abandon=true 면 방장이 finish 를 누르지 않고 떠난다(D6) - 30분 뒤 스윕이 닫아야 한다 */
export function journey(users, vu, { abandon = false } = {}) {
  const g = Math.floor((vu - 1) / GROUP);
  const role = (vu - 1) % GROUP;
  const me = users[vu - 1];
  const prefix = `LT-g${g}-`;

  check(appConfig(), { '1차 배포 모드(taxiEnabled=false)': (r) => r.status === 200 && r.json('taxiEnabled') === false });
  listRooms(me.token); sleep(POLL);                                       // 홈에서 지도를 잠깐 본다

  let partyId = null;
  if (role === 0) {
    const opened = Date.now();
    const r = openRoom(me.token, 3, 판교역, `${prefix}${opened}`);
    if (!check(r, { '방 생성 200': (x) => x.status === 200 })) { console.error(`open ${r.status}: ${r.body}`); sleep(POLL); return; }
    partyId = r.id;
    if (!pollUntil(me.token, partyId, ['COMPLETED'], WAIT_MAX)) { giveUps.add(1); leaveRoom(me.token, partyId); return; }
    fillTime.add(Date.now() - opened);
  } else {
    sleep(role);                                                          // 두 참여자가 같은 순간에 몰리지 않게
    for (let t = 0; t < WAIT_MAX && partyId === null; t += POLL) {
      const res = listRooms(me.token);
      const room = res.status === 200 ? res.json('list').find((p) => p.destination.startsWith(prefix)) : null;
      if (room) {
        const j = joinRoom(me.token, room.partyId);
        if (j.status === 200) { joinOutcome.add(1, { result: 'ok' }); partyId = room.partyId; break; }
        if (j.status === 409) joinOutcome.add(1, { result: 'full' });
        else { joinOutcome.add(1, { result: 'other' }); console.error(`join ${j.status}: ${j.body}`); }
      }
      sleep(POLL);
    }
    if (partyId === null) { giveUps.add(1); return; }
    if (!pollUntil(me.token, partyId, ['COMPLETED'], WAIT_MAX)) { giveUps.add(1); leaveRoom(me.token, partyId); return; }
  }

  chat(me, `g${g}r${role}`);

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

  if (role === 0) check(finishRoom(me.token, partyId), { '합승 종료 204': (r) => r.status === 204 });
  else pollUntil(me.token, partyId, ['FINISHED'], 60);
  sleep(POLL);
}
