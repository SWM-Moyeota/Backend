// 공통 헬퍼 - 모든 시나리오가 이 파일만 import 한다
import http from 'k6/http';

export const BASE = __ENV.BASE_URL || 'http://localhost:8080';
export const 강남역 = { name: '강남역', lat: 37.4979, lng: 127.0276 };
export const 판교역 = { lat: 37.3948, lng: 127.1112 };
// 강남 일대 뷰포트 - 목록 조회 기본값 (조를 모르는 스모크·옛 시나리오용)
export const 뷰포트 = { swLat: 37.49, swLng: 127.02, neLat: 37.51, neLng: 127.04 };

// 출발지 후보 - 서울 시내 역 30곳(좌표는 역 근처 대략값). 조 번호로 고르므로 같은 조는 항상 같은 곳에서 만난다.
//   · 한 점에 몰면 뷰포트가 아무것도 못 걸러 ACTIVE 방 전부가 목록 응답에 실린다 - 실제보다 훨씬 무거워
//     방이 쌓일수록 목록 조회가 느려지고 DB 가 먼저 죽는다(5,000명에서 10ms → 2초).
//   · 무작위 좌표를 쓰지 않는 이유: 경로 캐시 키가 좌표(소수 5자리)라 좌표가 다르면 방마다 네이버를 부른다.
//     정해진 N곳이면 경로도 N개로 끝난다(캐시 TTL 10분마다 최대 N회).
//   · 서로 ±0.005도(약 500m) 안에 겹치지 않게 골랐다 - viewportAround 한 번에 자기 출발지의 방만 들어온다.
export const 출발지들 = [
  강남역,
  { name: '역삼역', lat: 37.5007, lng: 127.0365 }, { name: '선릉역', lat: 37.5045, lng: 127.0490 },
  { name: '삼성역', lat: 37.5088, lng: 127.0631 }, { name: '잠실역', lat: 37.5133, lng: 127.1001 },
  { name: '교대역', lat: 37.4934, lng: 127.0142 }, { name: '서초역', lat: 37.4918, lng: 127.0078 },
  { name: '사당역', lat: 37.4766, lng: 126.9816 }, { name: '신림역', lat: 37.4842, lng: 126.9297 },
  { name: '홍대입구역', lat: 37.5572, lng: 126.9245 }, { name: '신촌역', lat: 37.5551, lng: 126.9368 },
  { name: '시청역', lat: 37.5657, lng: 126.9769 }, { name: '서울역', lat: 37.5547, lng: 126.9707 },
  { name: '종각역', lat: 37.5702, lng: 126.9831 }, { name: '건대입구역', lat: 37.5404, lng: 127.0692 },
  { name: '왕십리역', lat: 37.5612, lng: 127.0371 }, { name: '성수역', lat: 37.5446, lng: 127.0559 },
  { name: '합정역', lat: 37.5495, lng: 126.9139 }, { name: '여의도역', lat: 37.5216, lng: 126.9243 },
  { name: '영등포역', lat: 37.5156, lng: 126.9076 }, { name: '노량진역', lat: 37.5131, lng: 126.9426 },
  { name: '고속터미널역', lat: 37.5049, lng: 127.0049 }, { name: '양재역', lat: 37.4841, lng: 127.0346 },
  { name: '수서역', lat: 37.4874, lng: 127.1018 }, { name: '천호역', lat: 37.5386, lng: 127.1236 },
  { name: '혜화역', lat: 37.5822, lng: 127.0019 }, { name: '동대문역', lat: 37.5714, lng: 127.0098 },
  { name: '신도림역', lat: 37.5088, lng: 126.8912 }, { name: '구로디지털단지역', lat: 37.4852, lng: 126.9015 },
  { name: '압구정역', lat: 37.5270, lng: 127.0284 },
];
// SPOTS=1 이면 예전처럼 전부 강남역 한 점 - 분산 전 결과와 비교할 때만 쓴다
export const SPOTS = Math.max(1, Math.min(Number(__ENV.SPOTS || 출발지들.length), 출발지들.length));
const VIEW_DEG = Number(__ENV.VIEW_DEG || 0.005);

/** 조 g 의 출발지 */
export function spotOf(g) { return 출발지들[g % SPOTS]; }

/** 앱의 지도 화면 - 사용자가 서 있는 곳 주변만 조회한다 */
export function viewportAround(p, d = VIEW_DEG) {
  return { swLat: p.lat - d, swLng: p.lng - d, neLat: p.lat + d, neLng: p.lng + d };
}

export function json() {
  return { headers: { 'Content-Type': 'application/json' } };
}

export function auth(token, extra = {}) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, ...extra };
}

// 출발지는 출발지들 중 하나(경로 캐시 적중 - 네이버를 방마다 부르지 않는다). 같은 출발지의 방 구분은 destName 으로 한다
export function openRoom(token, capacity = 3, dest = 판교역, destName = '판교역', from = 강남역) {
  const res = http.post(`${BASE}/api/v1/matching/rooms`, JSON.stringify({
    departureLat: from.lat, departureLng: from.lng,
    destinationLat: dest.lat, destinationLng: dest.lng,
    departure: from.name, destination: destName,
    capacity, departureRadius: 100, destinationRadius: 100,
  }), auth(token, { tags: { name: 'POST /matching/rooms' } }));
  return { status: res.status, id: res.status === 200 ? res.json('id') : null, body: res.body };
}

export function leaveRoom(token, partyId) {
  return http.del(`${BASE}/api/v1/matching/leave/${partyId}`, null, auth(token, { tags: { name: 'DELETE /matching/leave' } }));
}

export function listRooms(token, view = 뷰포트) {
  const q = `swLat=${view.swLat}&swLng=${view.swLng}&neLat=${view.neLat}&neLng=${view.neLng}`;
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

// 채팅 화면의 폴링. cursor 는 마지막으로 본 메시지 id - 서버는 cursor < 1 을 400(CHAT_INVALID_CURSOR)으로 막는다
export function chatMessagesAfter(token, chatRoomId, cursor) {
  return http.get(`${BASE}/api/v1/chat-rooms/${chatRoomId}/messages/after?cursor=${cursor}&size=30`, auth(token, { tags: { name: 'GET /chat-rooms/{id}/messages/after' } }));
}

/** 앱의 폴링 한 번. 아직 메시지를 하나도 못 본 방(cursor 없음)은 after 를 못 쓰므로 첫 페이지를 다시 읽는다 */
export function pollChat(token, chatRoomId, cursor) {
  return cursor ? chatMessagesAfter(token, chatRoomId, cursor) : chatMessages(token, chatRoomId);
}

/** 응답에서 가장 큰 메시지 id. 없으면 넘겨받은 cursor 를 그대로 돌려준다 */
export function lastMessageId(res, cursor = null) {
  if (res.status !== 200) return cursor;
  const ids = res.json('messages').map((m) => m.id);
  return ids.length ? Math.max(cursor || 0, ...ids) : cursor;
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
  const r = openRoom(host.token, 3, 판교역, destName || `LT-g${g}`, spotOf(g));
  if (!r.id) return null;
  joinRoom(a.token, r.id);
  joinRoom(b.token, r.id);
  return r.id;
}

// ───────── 앱이 화면 진입 때 한 번씩 부르는 것들 ─────────

export function userInfo(token) {
  return http.get(`${BASE}/api/v1/local/users/info`, auth(token, { tags: { name: 'GET /local/users/info' } }));
}

export function favoritePlaces(token) {
  return http.get(`${BASE}/api/v1/users/me/favorite-places`, auth(token, { tags: { name: 'GET /users/me/favorite-places' } }));
}

// 방 만들기 확인 화면의 경로 미리보기. 출발지가 정해진 N곳이라 경로 캐시에 적중한다(TTL 10분마다 출발지당 1회만 네이버 호출)
export function previewRoute(token, from = 강남역) {
  return http.post(`${BASE}/api/v1/matching/routes`, JSON.stringify({
    departureLat: from.lat, departureLng: from.lng, destinationLat: 판교역.lat, destinationLng: 판교역.lng,
  }), auth(token, { tags: { name: 'POST /matching/routes' } }));
}

export function chatRoom(token, chatRoomId) {
  return http.get(`${BASE}/api/v1/chat-rooms/${chatRoomId}`, auth(token, { tags: { name: 'GET /chat-rooms/{id}' } }));
}

/** 앱 시작: 설정 → 내 정보 → 즐겨찾기(홈) → 진행 중인 방 찾기(기억이 없으면 채팅방 목록을 훑는다) */
export function appStart(token) {
  const config = appConfig();
  userInfo(token);
  favoritePlaces(token);
  myChatRooms(token);
  return config;
}

/** 채팅방을 열 때 앱이 부르는 3건: 첫 페이지 · 참여자(헤더 제목) · 방 정보. 마지막으로 본 메시지 id 를 돌려준다 */
export function openChatRoom(token, chatRoomId) {
  const page = chatMessages(token, chatRoomId);
  chatMembers(token, chatRoomId);
  chatRoom(token, chatRoomId);
  return { status: page.status, cursor: lastMessageId(page) };
}
