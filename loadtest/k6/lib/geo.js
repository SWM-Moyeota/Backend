// 강남역 주변에서 출발해 판교로 가는 파티를 만든다. 배차 탐색 초기 반경이 1km 라 기사는 출발지 500m 안에 둔다.
export const GANGNAM = { lat: 37.4979, lng: 127.0276 };
export const PANGYO = { lat: 37.3948, lng: 127.1112 };
export const SEOUL_STATION = { lat: 37.5547, lng: 126.9707 };

const M_PER_DEG_LAT = 111_320;

/** center 에서 반경 radiusM 안의 임의 지점 */
export function jitter(center, radiusM) {
  const r = radiusM * Math.sqrt(Math.random());
  const theta = Math.random() * 2 * Math.PI;
  const dLat = (r * Math.cos(theta)) / M_PER_DEG_LAT;
  const dLng = (r * Math.sin(theta)) / (M_PER_DEG_LAT * Math.cos((center.lat * Math.PI) / 180));
  return { lat: +(center.lat + dLat).toFixed(6), lng: +(center.lng + dLng).toFixed(6) };
}

/** 강남역 일대를 감싸는 지도 범위 (bbox 조회용) */
export function gangnamBbox() {
  return { swLat: 37.49, swLng: 127.02, neLat: 37.51, neLng: 127.04 };
}

/** 정원 capacity 인 파티 개설 요청 본문. 출발지는 강남역 반경 200m, 목적지는 판교 반경 300m */
export function partyRequest(capacity) {
  const dep = jitter(GANGNAM, 200);
  const dest = jitter(PANGYO, 300);
  return {
    departureLat: dep.lat, departureLng: dep.lng,
    destinationLat: dest.lat, destinationLng: dest.lng,
    departure: '강남역', destination: '판교역',
    capacity,
    departureRadius: 300, destinationRadius: 300,
  };
}
