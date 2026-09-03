package team.codingforest.moyeota.matching.application.dto;

public record OpenPartyRequest(Long creatorId, Double departureLat, Double departureLng,
                               Double destinationLat, Double destinationLng, String departure, String destination,
                               Integer capacity, Integer departureRadius, Integer destinationRadius) {

    /** creatorId는 토큰에서 온다. 본문의 creatorId 필드는 구버전 호환용으로 받기만 하고 무시한다 */
    public OpenPartyCommand toCommand(Long creatorMemberId) {
        return new OpenPartyCommand(creatorMemberId, departureLat, departureLng, destinationLat, destinationLng,
                                departure, destination, capacity, departureRadius, destinationRadius);
    }
}
