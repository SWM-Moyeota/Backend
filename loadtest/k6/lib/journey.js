// 승객 매칭 → 배차 → 운행 종료까지의 한 여정. ride-e2e 와 mixed 시나리오가 공유한다.
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { matching, dispatch, openAndFill } from './api.js';
import { partyRequest, jitter } from './geo.js';

export const metrics = {
  notifyMs: new Trend('dispatch_notify_ms', true),   // 마지막 참가(정원 충족) → 기사에게 콜이 열리기까지
  notified: new Rate('dispatch_notified'),            // 제한시간 안에 콜이 열린 비율
  journeyMs: new Trend('ride_journey_ms', true),     // 파티 개설 → 운행 종료
  journeyOk: new Rate('ride_journey_ok'),
  acceptConflicts: new Counter('dispatch_accept_conflicts'),
};

const POLL_INTERVAL_S = 0.25;
const POLL_TIMEOUT_MS = Number(__ENV.DISPATCH_TIMEOUT_MS || 15000);

/** 기사에게 콜이 열릴 때까지 상태를 폴링. 열리면 걸린 ms, 아니면 -1 */
export function waitForCall(driverToken, partyId, sinceMs) {
  const deadline = sinceMs + POLL_TIMEOUT_MS;
  while (Date.now() < deadline) {
    const res = dispatch.callStatus(driverToken, partyId);
    if (res.status === 200 && res.json('open') === true) {
      const ms = Date.now() - sinceMs;
      metrics.notifyMs.add(ms);
      metrics.notified.add(true);
      return ms;
    }
    sleep(POLL_INTERVAL_S);
  }
  metrics.notified.add(false);
  return -1;
}

/**
 * 한 파티의 전체 여정. passengerTokens[0] 이 개설, 나머지가 참가, driverToken 이 배차받아 운행한다.
 * 반환: 성공 여부
 */
export function rideJourney(passengerTokens, driverToken) {
  const start = Date.now();
  const body = partyRequest(passengerTokens.length);
  const departure = { lat: body.departureLat, lng: body.departureLng };

  // 기사가 출발지 근처(초기 탐색 반경 1km 안)에서 대기
  const online = dispatch.online(driverToken, jitter(departure, 300));
  if (!check(online, { '기사 온라인 204': (r) => r.status === 204 })) return finish(false, start);

  const partyId = openAndFill(passengerTokens, body, check);
  if (!partyId) return finish(false, start);
  const filledAt = Date.now();

  // 정원 충족 → MatchingStartedEvent → 비동기 배차 리스너 → Redis 후보 등록. 그때까지 폴링
  if (waitForCall(driverToken, partyId, filledAt) < 0) {
    check(null, { '배차 콜 수신 (제한시간 내)': () => false });
    return finish(false, start);
  }

  const accept = dispatch.accept(driverToken, partyId);
  if (accept.status === 409) metrics.acceptConflicts.add(1);
  if (!check(accept, { '콜 수락 204': (r) => r.status === 204 })) return finish(false, start);

  // 수락하면 GEO 에서 빠지므로 기사 앱처럼 위치를 다시 보고해야 승객이 기사 위치를 볼 수 있다
  dispatch.location(driverToken, jitter(departure, 200));
  const loc = dispatch.driverLocation(passengerTokens[0], partyId);
  check(loc, { '승객 기사위치 조회 200': (r) => r.status === 200 });

  const arrive = dispatch.arrive(driverToken, partyId);
  check(arrive, { '기사 도착 204': (r) => r.status === 204 });

  const board = dispatch.board(driverToken, partyId);
  if (!check(board, { '탑승 204': (r) => r.status === 204 })) return finish(false, start);

  const complete = dispatch.complete(driverToken, partyId, 12000);
  const ok = check(complete, { '운행 종료 204': (r) => r.status === 204 });
  return finish(ok, start);
}

function finish(ok, start) {
  metrics.journeyMs.add(Date.now() - start);
  metrics.journeyOk.add(ok);
  return ok;
}
