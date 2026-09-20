// 엔드포인트 래퍼. tags.name 으로 Grafana 에서 요청 종류별 p95 를 나눠 본다.
import http from 'k6/http';
import { BASE_URL } from './config.js';
import { authHeaders } from './auth.js';

const acceptStatuses = http.expectedStatuses(204, 409);   // accept 는 204, 경쟁에서 진 기사는 409 CALL_CLOSED 가 정상
export const BOARD_STATUSES = http.expectedStatuses(204, 403, 409); // dispatch-race 가 다중 승자 중 실제 배정자를 찾을 때 403(NOT_ASSIGNED_DRIVER) 은 예상 응답

export const matching = {
  open: (token, body) => http.post(`${BASE_URL}/api/v1/matching/rooms`, JSON.stringify(body), authHeaders(token, 'matching_open')),
  join: (token, partyId) => http.post(`${BASE_URL}/api/v1/matching/rooms/${partyId}/join`, null, authHeaders(token, 'matching_join')),
  joinRequest: (token, partyId) => ({ method: 'POST', url: `${BASE_URL}/api/v1/matching/rooms/${partyId}/join`, params: authHeaders(token, 'matching_join') }),
  detail: (token, partyId) => http.get(`${BASE_URL}/api/v1/matching/rooms/${partyId}`, authHeaders(token, 'matching_detail')),
  listWithin: (token, b) => http.get(`${BASE_URL}/api/v1/matching/rooms?swLat=${b.swLat}&swLng=${b.swLng}&neLat=${b.neLat}&neLng=${b.neLng}`, authHeaders(token, 'matching_list_bbox')),
  finish: (token, partyId) => http.post(`${BASE_URL}/api/v1/matching/rooms/${partyId}/finish`, null, authHeaders(token, 'matching_finish')),
  leave: (token, partyId) => http.del(`${BASE_URL}/api/v1/matching/leave/${partyId}`, null, authHeaders(token, 'matching_leave')),
};

export const dispatch = {
  online: (token, p) => http.post(`${BASE_URL}/api/v1/dispatch/online`, JSON.stringify({ latitude: p.lat, longitude: p.lng }), authHeaders(token, 'dispatch_online')),
  location: (token, p) => http.post(`${BASE_URL}/api/v1/dispatch/location`, JSON.stringify({ latitude: p.lat, longitude: p.lng }), authHeaders(token, 'dispatch_location')),
  offline: (token) => http.del(`${BASE_URL}/api/v1/dispatch/online`, null, authHeaders(token, 'dispatch_offline')),
  callStatus: (token, partyId) => http.get(`${BASE_URL}/api/v1/dispatch/calls/${partyId}/status`, authHeaders(token, 'dispatch_call_status')),
  accept: (token, partyId) => http.post(`${BASE_URL}/api/v1/dispatch/calls/${partyId}/accept`, null, { ...authHeaders(token, 'dispatch_accept'), responseCallback: acceptStatuses }),
  acceptRequest: (token, partyId) => ({ method: 'POST', url: `${BASE_URL}/api/v1/dispatch/calls/${partyId}/accept`, params: { ...authHeaders(token, 'dispatch_accept'), responseCallback: acceptStatuses } }),
  reject: (token, partyId) => http.post(`${BASE_URL}/api/v1/dispatch/calls/${partyId}/reject`, null, authHeaders(token, 'dispatch_reject')),
  arrive: (token, partyId) => http.post(`${BASE_URL}/api/v1/dispatch/rides/${partyId}/arrive`, null, authHeaders(token, 'ride_arrive')),
  board: (token, partyId, params = {}) => http.post(`${BASE_URL}/api/v1/dispatch/rides/${partyId}/board`, null, { ...authHeaders(token, 'ride_board'), ...params }),
  complete: (token, partyId, fare) => http.post(`${BASE_URL}/api/v1/dispatch/rides/${partyId}/complete`, JSON.stringify({ fare }), authHeaders(token, 'ride_complete')),
  driverLocation: (token, partyId) => http.get(`${BASE_URL}/api/v1/dispatch/rides/${partyId}`, authHeaders(token, 'ride_driver_location')),
};

/** 정원 capacity 파티를 개설하고 나머지 승객이 동시에 참가한다. 반환: partyId (실패 시 null) */
export function openAndFill(passengerTokens, body, check) {
  const openRes = matching.open(passengerTokens[0], body);
  if (!check(openRes, { '파티 개설 200': (r) => r.status === 200 })) return null;
  const partyId = openRes.json('id');

  const joiners = passengerTokens.slice(1);
  if (joiners.length > 0) {
    const joinRes = http.batch(joiners.map((t) => matching.joinRequest(t, partyId)));
    const allJoined = joinRes.every((r) => r.status === 200);
    check(joinRes[0], { '파티 참가 전원 200': () => allJoined });
    if (!allJoined) return null;
  }
  return partyId;
}
