// D0 - 스모크. 1차 배포 여정을 한 번 끝까지 돈다 (users[0..2] 사용). 다른 시나리오 전에 반드시 먼저.
//   k6 run loadtest/d0-smoke.js
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import {
  appStart, listRooms, favoritePlaces, previewRoute, openRoom, joinRoom, leaveRoom, roomDetail, finishRoom,
  resolveChatRoomId, chatMembers, chatMessages, openChatRoom, pollChat, 판교역,
} from './lib/api.js';
import { chatSession } from './lib/stomp.js';

const users = new SharedArray('users', () => JSON.parse(open('./users.json')));

export const options = { vus: 1, iterations: 1, thresholds: { checks: ['rate==1'] } };

export default function () {
  const [host, a, b] = [users[0], users[1], users[2]];

  check(appStart(host.token), { 'taxiEnabled=false (택시 꺼진 서버인가)': (r) => r.status === 200 && r.json('taxiEnabled') === false });
  check(listRooms(host.token), { '지도 목록 200': (r) => r.status === 200 });
  check(favoritePlaces(host.token), { '즐겨찾기 200': (r) => r.status === 200 });
  check(previewRoute(host.token), { '경로 미리보기 200 (캐시가 비어 있으면 네이버를 1회 부른다)': (r) => r.status === 200 });

  const room = openRoom(host.token, 3, 판교역, 'LT-smoke');
  if (!check(room, { '방 생성 200': (r) => r.status === 200 })) { console.error(room.body); return; }
  check(joinRoom(a.token, room.id), { '참여 1': (r) => r.status === 200 });
  check(joinRoom(b.token, room.id), { '참여 2': (r) => r.status === 200 });

  const detail = roomDetail(host.token, room.id);
  check(detail, {
    '정원이 차면 COMPLETED 에 머문다 (MATCHING 이면 택시가 켜진 서버)': (r) => r.json('status') === 'COMPLETED',
    '멤버 3명': (r) => r.json('members').length === 3,
  });

  const chatRoomId = resolveChatRoomId(a.token, sleep);
  if (check(chatRoomId, { '참여자가 채팅방에 자동 입장됐다': (id) => id !== null })) {
    const members = chatMembers(a.token, chatRoomId);
    check(members, { '채팅방 멤버 3명': (r) => r.status === 200 && r.json().filter((m) => m.active).length === 3 });

    const opened = openChatRoom(a.token, chatRoomId);
    check(opened, { '채팅방 열기(첫 페이지·참여자·방 정보) 200': (o) => o.status === 200 });
    let got = 0, err = null;
    chatSession(a.token, chatRoomId, 'smoke', { holdSec: 8, sendEverySec: 60, cursor: opened.cursor, partyId: room.id, onLatency: () => { got++; }, onError: (e) => { err = e; } });
    check(null, { 'WebSocket 으로 보낸 메시지를 되받았다': () => got >= 1, 'STOMP 에러 없음': () => err === null });
    if (err) console.error(err);
    const saved = chatMessages(a.token, chatRoomId);
    check(saved, { '메시지가 저장됐다': (r) => r.status === 200 && r.json('messages').length >= 1 });
    const lastId = saved.status === 200 && saved.json('messages').length ? Math.max(...saved.json('messages').map((m) => m.id)) : null;
    check(pollChat(a.token, chatRoomId, lastId), { '커서 폴링(after) 200': (r) => r.status === 200 });
  }

  check(finishRoom(b.token, room.id), { '아무나 한 명이 종료할 수 있다 (204)': (r) => r.status === 204 });
  check(roomDetail(host.token, room.id), { 'FINISHED': (r) => r.json('status') === 'FINISHED' });

  const again = openRoom(host.token, 3, 판교역, 'LT-smoke-2');
  check(again, { '종료 뒤 새 방을 만들 수 있다': (r) => r.status === 200 });
  if (again.id) leaveRoom(host.token, again.id);
}
