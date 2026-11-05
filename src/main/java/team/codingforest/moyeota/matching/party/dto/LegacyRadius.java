package team.codingforest.moyeota.matching.party.dto;

/**
 *  방 응답(생성·예약 생성·상세·SSE connected)의 departureRadius·destinationRadius 에 넣는 고정값(m).
 *  서버는 반경을 쓰지 않는다 - 실시간 매칭 후보는 300m, 출발지 도착은 100m 상수로 재고, 방에도 저장하지 않는다(match_room 의 두 컬럼은 V31 기본값으로 채워진다).
 *  이미 출시된 승객 앱(강제 업데이트 없음)이 두 키를 null 이 될 수 없는 정수로 읽으므로 키를 빼거나 null 로 보내면 응답 해석이 깨진다 - 옛 버전 앱이 사라지면 키와 함께 지운다
 */
final class LegacyRadius {
    static final int METERS = 500;

    private LegacyRadius() {}
}
