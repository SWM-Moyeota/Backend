package team.codingforest.moyeota.matching.party.dto;

import java.time.Instant;

// taxiDriverId 는 택시(기사) 기능을 없애 늘 null 이다. 이미 출시된 앱(강제 업데이트 없음)이 이 키를 읽어 null 이 아닐 때만 기사 화면으로 가므로 응답에 남겨 둔다 - 옛 버전 앱이 사라지면 지운다
public record OpenPartyResponse(Long id, Double departureLat, Double departureLng,
                                Double destinationLat, Double destinationLng, String departure, String destination,
                                Integer capacity, Integer currentMembers, Integer departureRadius, Integer destinationRadius,
                                String status, Instant createdAt, Integer estimateFare, Integer estimateTime, String route,
                                Long taxiDriverId, String roomType, Instant departureAt) {

    public static OpenPartyResponse from(PartyResult party) {
        return new OpenPartyResponse(party.id(), party.departureLat(), party.departureLng(),
                                    party.destinationLat(), party.destinationLng(), party.departure(), party.destination(),
                                    party.capacity(), party.currentMembers(), party.departureRadius(), party.destinationRadius(),
                                    party.status(), party.createdAt(), party.estimateFare(), party.estimateTime(), party.route(), null,
                                    party.roomType(), party.departureAt());
    }
}
