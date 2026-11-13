// D0 - 스모크. 여정을 한 번 돈다 (users[0..2] 사용). 다른 시나리오 전에 반드시 먼저.
//   기본은 약속 확정·도착까지 보고 방을 나가서 닫는다(약 30초).
//   FULL=1 이면 약속 시간(확정 5분 뒤)까지 기다렸다가 출발을 눌러 FINISHED 까지 본다(약 6분).
//   k6 run loadtest/d0-smoke.js
//   k6 run -e FULL=1 loadtest/d0-smoke.js
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import {
  appStart, listRooms, favoritePlaces, previewRoute, openRoom, joinRoom, leaveRoom, roomDetail, confirmRoom, arriveRoom, departRoom, disbandRoom,
  resolveChatRoomId, chatMembers, chatMessages, openChatRoom, pollChat, 판교역, 출발지들, SPOTS,
} from './lib/api.js';
import { chatSession } from './lib/stomp.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));

const FULL = __ENV.FULL === '1';

export const options = { vus: 1, iterations: 1, thresholds: { checks: ['rate==1'] } };

export default function () {
  const [host, a, b] = [users[0], users[1], users[2]];

  check(appStart(host.token), { 'taxiEnabled=false (택시 꺼진 서버인가)': (r) => r.status === 200 && r.json('taxiEnabled') === false });
  check(listRooms(host.token), { '지도 목록 200': (r) => r.status === 200 });
  check(favoritePlaces(host.token), { '즐겨찾기 200': (r) => r.status === 200 });
  check(previewRoute(host.token), { '경로 미리보기 200 (캐시가 비어 있으면 네이버를 1회 부른다)': (r) => r.status === 200 });

  // CHECK_SPOTS=1 - 분산 출발지 전부(출발지 → 같은 도시의 짝)의 경로가 구해지는지 확인한다(네이버 최대 SPOTS 회). 좌표를 바꿨을 때 한 번만 돌린다
  if (__ENV.CHECK_SPOTS === '1') {
    for (const spot of 출발지들.slice(0, SPOTS)) {
      const res = previewRoute(host.token, spot);
      if (!check(res, { '출발지별 경로 200': (r) => r.status === 200 })) console.error(`경로 실패 ${spot.city} ${spot.name} → ${spot.dest.name} ${res.status}: ${res.body}`);
    }
  }

  const room = openRoom(host.token, 3, 판교역, 'LT-smoke');
  if (!check(room, { '방 생성 200': (r) => r.status === 200 })) { console.error(room.body); return; }
  const created = roomDetail(host.token, room.id);
  const v0 = created.status === 200 ? created.json('version') : null;
  check(v0, {
    '방 상세에 정수 version 이 있다 (없으면 방 버전 전 서버)': (v) => Number.isInteger(v),
    '생성 직후 version 0': (v) => v === 0,
  });
  check(joinRoom(a.token, room.id), { '참여 1': (r) => r.status === 200 });
  // join 응답은 {status, currentMembers} 만 준다 (지문 필드는 서버에서 빠졌다)
  check(joinRoom(b.token, room.id), {
    '참여 2': (r) => r.status === 200,
    'join 응답에 status·currentMembers 가 있다 (정원이 차도 ACTIVE, 3명)': (r) => r.status === 200 && r.json('status') === 'ACTIVE' && r.json('currentMembers') === 3,
    'join 응답에 fingerprint 가 없다 (변경분 SSE 가 배포된 서버인가)': (r) => r.status === 200 && r.json('fingerprint') === undefined,
  });

  const detail = roomDetail(host.token, room.id);
  const v2 = detail.status === 200 ? detail.json('version') : null;
  check(detail, {
    '정원이 차도 ACTIVE 그대로다 (COMPLETED 면 방 상태 재정의 전 서버)': (r) => r.json('status') === 'ACTIVE',
    '확정 전에는 confirmed=false, departureAt=null': (r) => r.json('confirmed') === false && r.json('departureAt') === null,
    '멤버 3명': (r) => r.json('members').length === 3,
    // 방 상세는 SSE connected 의 data 와 같은 JSON 이다. 지문·입장 시각은 서버에서 빠졌다 - 여기서 떨어지면 옛 서버다
    '방 상세에 fingerprint 가 없다': (r) => r.json('fingerprint') === undefined,
    '방 상세 members 에 joinedAt 이 없다': (r) => r.json('members').every((m) => m.joinedAt === undefined),
    '참여 2번에 version 이 2 오른다 (0 → 2)': () => Number.isInteger(v0) && Number.isInteger(v2) && v2 === v0 + 2,
  });

  const chatRoomId = resolveChatRoomId(a.token, sleep);
  if (check(chatRoomId, { '참여자가 채팅방에 자동 입장됐다': (id) => id !== null })) {
    const members = chatMembers(a.token, chatRoomId);
    check(members, { '채팅방 멤버 3명': (r) => r.status === 200 && r.json().filter((m) => m.active).length === 3 });

    const opened = openChatRoom(a.token, chatRoomId);
    check(opened, { '채팅방 열기(첫 페이지·참여자·방 정보) 200': (o) => o.status === 200 });
    let got = 0, err = null;
    chatSession(a.token, chatRoomId, 'smoke', { holdSec: 8, sendEverySec: 60, cursor: opened.cursor, onLatency: () => { got++; }, onError: (e) => { err = e; } });
    check(null, { 'WebSocket 으로 보낸 메시지를 되받았다': () => got >= 1, 'STOMP 에러 없음': () => err === null });
    if (err) console.error(err);
    const saved = chatMessages(a.token, chatRoomId);
    check(saved, { '메시지가 저장됐다': (r) => r.status === 200 && r.json('messages').length >= 1 });
    const lastId = saved.status === 200 && saved.json('messages').length ? Math.max(...saved.json('messages').map((m) => m.id)) : null;
    check(pollChat(a.token, chatRoomId, lastId), { '커서 폴링(after) 200': (r) => r.status === 200 });
  }

  // 약속 확정 → 도착 → 출발
  check(arriveRoom(a.token, room.id), { '확정 전에는 도착할 수 없다 (409 PARTY_NOT_CONFIRMED)': (r) => r.status === 409 && r.body.includes('PARTY_NOT_CONFIRMED') });
  check(confirmRoom(b.token, room.id), { '방장이 아니면 확정할 수 없다 (403)': (r) => r.status === 403 });
  const confirmed = confirmRoom(host.token, room.id);
  check(confirmed, { '방장이 약속을 확정한다 (204)': (r) => r.status === 204 });
  if (confirmed.status !== 204) console.error(`confirm ${confirmed.status}: ${confirmed.body}`);
  check(roomDetail(host.token, room.id), {
    '확정하면 COMPLETED': (r) => r.json('status') === 'COMPLETED',
    '확정 뒤 confirmed=true, departureAt 이 있다': (r) => r.json('confirmed') === true && r.json('departureAt') !== null,
    '확정에 version 이 1 오른다': (r) => Number.isInteger(v2) && r.json('version') === v2 + 1,
  });
  check(arriveRoom(a.token, room.id), { '참여자 도착 204': (r) => r.status === 204 });
  check(arriveRoom(host.token, room.id), { '방장 도착 204': (r) => r.status === 204 });
  check(arriveRoom(host.token, room.id), { '다시 눌러도 204': (r) => r.status === 204 });
  check(roomDetail(host.token, room.id), {
    '도착한 멤버 2명이 arrived=true': (r) => r.json('members').filter((m) => m.arrived).length === 2,
    '도착 2번에 version 이 2 오른다 (다시 누른 것은 안 오른다)': (r) => Number.isInteger(v2) && r.json('version') === v2 + 3,
  });
  check(departRoom(b.token, room.id), { '방장이 아니면 출발할 수 없다 (403)': (r) => r.status === 403 });
  check(departRoom(host.token, room.id), { '약속 시간 전에는 출발할 수 없다 (409 MEETING_NOT_STARTED)': (r) => r.status === 409 && r.body.includes('MEETING_NOT_STARTED') });

  if (FULL) {
    sleep(Math.max(0, (confirmed.departureAt - Date.now()) / 1000) + 2);      // 약속 시간까지
    check(departRoom(host.token, room.id), { '약속 시간 뒤 방장이 출발한다 (204)': (r) => r.status === 204 });
    check(roomDetail(host.token, room.id), {
      'FINISHED': (r) => r.json('status') === 'FINISHED',
      '출발에 version 이 1 오른다': (r) => Number.isInteger(v2) && r.json('version') === v2 + 4,
    });
  } else {
    disbandRoom([host, a, b], room.id);                                       // 출발까지 5분을 기다리지 않고 닫는다
    check(roomDetail(host.token, room.id), { '모두 나가면 CANCELED': (r) => r.json('status') === 'CANCELED' });
  }

  const again = openRoom(host.token, 3, 판교역, 'LT-smoke-2');
  check(again, { '방이 닫힌 뒤 새 방을 만들 수 있다': (r) => r.status === 200 });
  if (again.id) leaveRoom(host.token, again.id);
}
