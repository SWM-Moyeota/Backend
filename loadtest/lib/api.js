// 공통 헬퍼 - 모든 시나리오가 이 파일만 import 한다
import http from 'k6/http';

export const BASE = __ENV.BASE_URL || 'http://localhost:8080';
export const 강남역 = { lat: 37.4979, lng: 127.0276 };
export const 판교역 = { lat: 37.3948, lng: 127.1112 };
// 강남 일대 뷰포트 - 목록 조회 파라미터
export const 뷰포트 = { swLat: 37.49, swLng: 127.02, neLat: 37.51, neLng: 127.04 };

export function json() {
  return { headers: { 'Content-Type': 'application/json' } };
}

export function auth(token, extra = {}) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, ...extra };
}

// 좌표는 고정(경로 캐시 적중 - 네이버 API 를 다시 부르지 않는다). 방 구분은 destName 으로만 한다
export function openRoom(token, capacity = 3, dest = 판교역, destName = '판교역') {
  const res = http.post(`${BASE}/api/v1/matching/rooms`, JSON.stringify({
    departureLat: 강남역.lat, departureLng: 강남역.lng,
    destinationLat: dest.lat, destinationLng: dest.lng,
    departure: '강남역', destination: destName,
    capacity, departureRadius: 100, destinationRadius: 100,
  }), auth(token, { tags: { name: 'POST /matching/rooms' } }));
  return { status: res.status, id: res.status === 200 ? res.json('id') : null, body: res.body };
}

export function leaveRoom(token, partyId) {
  return http.del(`${BASE}/api/v1/matching/leave/${partyId}`, null, auth(token, { tags: { name: 'DELETE /matching/leave' } }));
}

export function listRooms(token) {
  const q = `swLat=${뷰포트.swLat}&swLng=${뷰포트.swLng}&neLat=${뷰포트.neLat}&neLng=${뷰포트.neLng}`;
  return http.get(`${BASE}/api/v1/matching/rooms?${q}`, auth(token, { tags: { name: 'GET /matching/rooms (viewport)' } }));
}

export function roomDetail(token, partyId) {
  return http.get(`${BASE}/api/v1/matching/rooms/${partyId}`, auth(token, { tags: { name: 'GET /matching/rooms/{id}' } }));
}

export function myChatRooms(token) {
  return http.get(`${BASE}/api/v1/chat-rooms/me`, auth(token, { tags: { name: 'GET /chat-rooms/me' } }));
}

export function joinRoom(token, partyId) {
  return http.post(`${BASE}/api/v1/matching/rooms/${partyId}/join`, null, auth(token, { tags: { name: 'POST /matching/rooms/{id}/join' } }));
}

// ───────── 1차 배포(택시 꺼짐) 시나리오용 ─────────

export function appConfig() {
  return http.get(`${BASE}/api/v1/config`, { tags: { name: 'GET /config' } });
}

export function finishRoom(token, partyId) {
  return http.post(`${BASE}/api/v1/matching/rooms/${partyId}/finish`, null, auth(token, { tags: { name: 'POST /matching/rooms/{id}/finish' } }));
}

export function chatMembers(token, chatRoomId) {
  return http.get(`${BASE}/api/v1/chat-rooms/${chatRoomId}/users`, auth(token, { tags: { name: 'GET /chat-rooms/{id}/users' } }));
}

export function chatMessages(token, chatRoomId) {
  return http.get(`${BASE}/api/v1/chat-rooms/${chatRoomId}/messages?size=30`, auth(token, { tags: { name: 'GET /chat-rooms/{id}/messages' } }));
}

// 채팅 화면의 폴링 안전망 (소켓이 살아 있으면 20초, 죽으면 3초)
export function chatMessagesAfter(token, chatRoomId, cursor) {
  return http.get(`${BASE}/api/v1/chat-rooms/${chatRoomId}/messages/after?cursor=${cursor}&size=30`, auth(token, { tags: { name: 'GET /chat-rooms/{id}/messages/after' } }));
}

export function sendChatRest(token, chatRoomId, content) {
  return http.post(`${BASE}/api/v1/chat-rooms/${chatRoomId}/messages`, JSON.stringify({ content }), auth(token, { tags: { name: 'POST /chat-rooms/{id}/messages' } }));
}

/** 사용자는 진행 중인 방에 하나만 들어갈 수 있어 /chat-rooms/me 의 첫 항목이 곧 지금 방의 채팅방이다. 입장은 비동기라 잠깐 기다린다 */
export function resolveChatRoomId(token, sleepFn, tries = 10) {
  for (let i = 0; i < tries; i++) {
    const res = myChatRooms(token);
    if (res.status === 200) {
      const rooms = res.json();
      if (rooms.length > 0) return rooms[0].chatRoomId;
    }
    sleepFn(1);
  }
  return null;
}

// 3명 한 조 - 조 g 의 방장은 users[3g], 참여자는 users[3g+1], users[3g+2]
export const GROUP = 3;
export function groupUsers(users, g) { return [users[g * GROUP], users[g * GROUP + 1], users[g * GROUP + 2]]; }

/** 조 g 로 정원 3인 방을 채워 COMPLETED 로 만든다. setup 전용 */
export function fillRoom(users, g, destName) {
  const [host, a, b] = groupUsers(users, g);
  const r = openRoom(host.token, 3, 판교역, destName || `LT-g${g}`);
  if (!r.id) return null;
  joinRoom(a.token, r.id);
  joinRoom(b.token, r.id);
  return r.id;
}
