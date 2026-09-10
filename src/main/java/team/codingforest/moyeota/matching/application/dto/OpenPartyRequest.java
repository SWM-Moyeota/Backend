package team.codingforest.moyeota.matching.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
public record OpenPartyRequest(@Schema(description = "무시됨 - 생성자는 토큰에서", deprecated = true) Long creatorId,
                               @Schema(example = "37.4979") Double departureLat, @Schema(example = "127.0276") Double departureLng,
                               @Schema(example = "37.3948") Double destinationLat, @Schema(example = "127.1112") Double destinationLng,
                               @Schema(example = "강남역") String departure, @Schema(example = "판교역") String destination,
                               @Schema(description = "정원(생성자 포함) 2~4", example = "3") Integer capacity,
                               @Schema(description = "출발지 허용 반경(m)", example = "100") Integer departureRadius,
                               @Schema(description = "목적지 허용 반경(m)", example = "100") Integer destinationRadius) {

    /** creatorId는 토큰에서 온다. 본문의 creatorId 필드는 구버전 호환용으로 받기만 하고 무시한다 */
    public OpenPartyCommand toCommand(Long creatorMemberId) {
        return new OpenPartyCommand(creatorMemberId, departureLat, departureLng, destinationLat, destinationLng,
                                departure, destination, capacity, departureRadius, destinationRadius);
    }
}
