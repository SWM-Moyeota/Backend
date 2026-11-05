package team.codingforest.moyeota.matching.party.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// taxiDriverId 는 택시(기사) 기능을 없애 늘 null 이다. 이미 출시된 앱(강제 업데이트 없음)이 이 키를 읽어 null 이 아닐 때만 기사 화면으로 가므로 응답에 남겨 둔다 - 옛 버전 앱이 사라지면 지운다
public record OpenPartyResponse(Long id, Double departureLat, Double departureLng,
                                Double destinationLat, Double destinationLng, String departure, String destination,
                                Integer capacity, Integer currentMembers,
                                @Schema(description = "출발지 반경(m). 서버가 쓰지 않아 늘 500 이다(출시 앱 호환용으로 키만 남김)", example = "500") Integer departureRadius,
                                @Schema(description = "도착지 반경(m). 서버가 쓰지 않아 늘 500 이다(출시 앱 호환용으로 키만 남김)", example = "500") Integer destinationRadius,
                                String status, Instant createdAt, Integer estimateFare, Integer estimateTime, String route,
                                Long taxiDriverId, String roomType, Instant departureAt) {

    public static OpenPartyResponse from(PartyResult party) {
        return new OpenPartyResponse(party.id(), party.departureLat(), party.departureLng(),
                                    party.destinationLat(), party.destinationLng(), party.departure(), party.destination(),
                                    party.capacity(), party.currentMembers(), LegacyRadius.METERS, LegacyRadius.METERS,
                                    party.status(), party.createdAt(), party.estimateFare(), party.estimateTime(), party.route(), null,
                                    party.roomType(), party.departureAt());
    }
}
