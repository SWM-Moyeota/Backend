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

export function openRoom(token, capacity = 3, dest = 판교역) {
  const res = http.post(`${BASE}/api/v1/matching/rooms`, JSON.stringify({
    departureLat: 강남역.lat, departureLng: 강남역.lng,
    destinationLat: dest.lat, destinationLng: dest.lng,
    departure: '강남역', destination: '판교역',
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
