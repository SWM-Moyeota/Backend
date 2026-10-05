// 공통 헬퍼 - 모든 시나리오가 이 파일만 import 한다
import http from 'k6/http';

export const BASE = __ENV.BASE_URL || 'http://localhost:8080';
export const 판교역 = { name: '판교역', lat: 37.3948, lng: 127.1112 };
export const 강남역 = { name: '강남역', lat: 37.4979, lng: 127.0276, dest: 판교역 };
// 강남 일대 뷰포트 - 목록 조회 기본값 (조를 모르는 스모크·옛 시나리오용)
export const 뷰포트 = { swLat: 37.49, swLng: 127.02, neLat: 37.51, neLng: 127.04 };

// 출발지 후보 - 전국 21개 도시 100곳(좌표는 역·시청 근처 대략값). 조 번호로 고르므로 같은 조는 항상 같은 곳에서 만난다.
//   · 한 점에 몰면 뷰포트가 아무것도 못 걸러 ACTIVE 방 전부가 목록 응답에 실린다 - 실제보다 훨씬 무거워
//     방이 쌓일수록 목록 조회가 느려지고 DB 가 먼저 죽는다(5,000명에서 10ms → 2초).
//   · 무작위 좌표를 쓰지 않는 이유: 경로 캐시 키가 좌표(소수 5자리)라 좌표가 다르면 방마다 네이버를 부른다.
//     정해진 N곳이면 경로도 N개로 끝난다(캐시 TTL 10분마다 최대 N회).
//   · 서로 ±0.005도(약 500m) 안에 겹치지 않게 골랐다 - viewportAround 한 번에 자기 출발지의 방만 들어온다.
//   · 목적지는 같은 도시의 다음 출발지다 - 부산에서 판교로 가는 400km 경로가 생기지 않게 한다.
const 도시들 = {
  서울: [
    ['강남역', 37.4979, 127.0276], ['역삼역', 37.5007, 127.0365], ['선릉역', 37.5045, 127.0490], ['삼성역', 37.5088, 127.0631],
    ['잠실역', 37.5133, 127.1001], ['천호역', 37.5386, 127.1236], ['건대입구역', 37.5404, 127.0692], ['성수역', 37.5446, 127.0559],
    ['왕십리역', 37.5612, 127.0371], ['동대문역', 37.5714, 127.0098], ['혜화역', 37.5822, 127.0019], ['종각역', 37.5702, 126.9831],
    ['시청역', 37.5657, 126.9769], ['서울역', 37.5547, 126.9707], ['신촌역', 37.5551, 126.9368], ['홍대입구역', 37.5572, 126.9245],
    ['합정역', 37.5495, 126.9139], ['여의도역', 37.5216, 126.9243], ['영등포역', 37.5156, 126.9076], ['신도림역', 37.5088, 126.8912],
    ['구로디지털단지역', 37.4852, 126.9015], ['신림역', 37.4842, 126.9297], ['노량진역', 37.5131, 126.9426], ['사당역', 37.4766, 126.9816],
    ['고속터미널역', 37.5049, 127.0049], ['교대역', 37.4934, 127.0142], ['양재역', 37.4841, 127.0346], ['수서역', 37.4874, 127.1018],
  ],
  성남: [['판교역', 37.3948, 127.1112], ['서현역', 37.3850, 127.1233], ['정자역', 37.3670, 127.1081], ['야탑역', 37.4113, 127.1286], ['모란역', 37.4321, 127.1291]],
  수원: [['수원역', 37.2660, 127.0001], ['수원시청', 37.2636, 127.0286], ['광교중앙역', 37.2886, 127.0516]],
  고양: [['정발산역', 37.6595, 126.7733], ['대화역', 37.6761, 126.7474], ['화정역', 37.6346, 126.8327]],
  부천: [['부천역', 37.4840, 126.7827], ['상동역', 37.5058, 126.7531]],
  안양: [['안양역', 37.4016, 126.9228], ['범계역', 37.3897, 126.9508]],
  인천: [['부평역', 37.4895, 126.7245], ['인천시청역', 37.4576, 126.7023], ['주안역', 37.4650, 126.6800], ['센트럴파크역', 37.3930, 126.6345]],
  부산: [
    ['부산역', 35.1152, 129.0422], ['남포역', 35.0979, 129.0346], ['서면역', 35.1578, 129.0592], ['연산역', 35.1861, 129.0815],
    ['동래역', 35.2056, 129.0786], ['부산대역', 35.2296, 129.0894], ['사상역', 35.1626, 128.9847], ['경성대부경대역', 35.1376, 129.1005],
    ['광안역', 35.1577, 129.1130], ['센텀시티역', 35.1690, 129.1320], ['해운대역', 35.1637, 129.1588],
  ],
  대구: [
    ['동대구역', 35.8797, 128.6285], ['대구역', 35.8762, 128.5961], ['반월당역', 35.8656, 128.5934], ['범어역', 35.8594, 128.6249],
    ['수성못역', 35.8300, 128.6230], ['두류역', 35.8570, 128.5560], ['상인역', 35.8188, 128.5372],
  ],
  대전: [
    ['대전역', 36.3324, 127.4342], ['대전복합터미널', 36.3496, 127.4370], ['서대전역', 36.3226, 127.4038], ['대전시청역', 36.3510, 127.3850],
    ['정부청사역', 36.3590, 127.3810], ['유성온천역', 36.3536, 127.3415],
  ],
  광주: [['광주송정역', 35.1378, 126.7914], ['광주종합버스터미널', 35.1603, 126.8793], ['금남로4가역', 35.1525, 126.9140], ['광주역', 35.1654, 126.9092], ['전남대', 35.1760, 126.9090]],
  울산: [['울산시청', 35.5395, 129.3115], ['울산고속버스터미널', 35.5380, 129.3380], ['태화강역', 35.5390, 129.3540]],
  세종: [['세종시청', 36.4800, 127.2890], ['정부세종청사', 36.5040, 127.2650]],
  청주: [['청주시청', 36.6420, 127.4890], ['청주시외버스터미널', 36.6250, 127.4310]],
  천안아산: [['천안역', 36.8100, 127.1460], ['천안아산역', 36.7945, 127.1045], ['온양온천역', 36.7806, 127.0030]],
  전주: [['전주역', 35.8497, 127.1617], ['전주시청', 35.8242, 127.1480], ['전주한옥마을', 35.8150, 127.1530]],
  창원: [['창원중앙역', 35.2570, 128.7060], ['창원시청', 35.2280, 128.6810], ['마산역', 35.2360, 128.5770]],
  포항: [['포항시청', 36.0190, 129.3435], ['포항역', 36.0720, 129.3420]],
  춘천: [['춘천역', 37.8847, 127.7170], ['춘천시청', 37.8813, 127.7298]],
  강릉: [['강릉역', 37.7640, 128.8990], ['강릉시청', 37.7519, 128.8760]],
  제주: [['제주공항', 33.5070, 126.4930], ['제주시청', 33.4996, 126.5312]],
};

// 도시별 목록을 { name, city, lat, lng, dest } 로 만들고, 도시를 번갈아 가며 한 줄로 편다.
// 번갈아 펴는 이유: GROUPS=20 같은 작은 실행도 서울만이 아니라 여러 도시에 걸치게 하려는 것. 0번은 항상 강남역이다.
export const 출발지들 = (() => {
  const byCity = Object.entries(도시들).map(([city, list]) => {
    const spots = list.map(([name, lat, lng]) => ({ name, city, lat, lng }));
    spots.forEach((s, i) => { const d = spots[(i + 1) % spots.length]; s.dest = { name: d.name, lat: d.lat, lng: d.lng }; });
    return spots;
  });
  const flat = [];
  for (let i = 0; flat.length < byCity.reduce((n, c) => n + c.length, 0); i++) byCity.forEach((c) => { if (c[i]) flat.push(c[i]); });
  flat[0].dest = { name: '판교역', lat: 판교역.lat, lng: 판교역.lng };   // 강남역 → 판교역 은 옛 시나리오와 경로 캐시를 같이 쓴다
  return flat;
})();
// SPOTS=1 이면 예전처럼 전부 강남역 → 판교역 한 쌍 - 분산 전 결과와 비교할 때만 쓴다
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

// 출발지는 출발지들 중 하나(경로 캐시 적중 - 네이버를 방마다 부르지 않는다). 같은 출발지의 방 구분은 destName 으로 한다.
// 분산해서 만들 때는 openRoom(token, 3, spot.dest, 이름, spot) - 목적지 좌표도 그 출발지의 짝을 쓴다
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
  const from = spotOf(g);
  const r = openRoom(host.token, 3, from.dest, destName || `LT-g${g}`, from);
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
  const dest = from.dest || 판교역;
  return http.post(`${BASE}/api/v1/matching/routes`, JSON.stringify({
    departureLat: from.lat, departureLng: from.lng, destinationLat: dest.lat, destinationLng: dest.lng,
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
