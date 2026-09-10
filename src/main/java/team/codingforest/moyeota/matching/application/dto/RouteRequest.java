package team.codingforest.moyeota.matching.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
public record RouteRequest(@Schema(example = "37.4979") double departureLat, @Schema(example = "127.0276") double departureLng,
                           @Schema(example = "37.3948") double destinationLat, @Schema(example = "127.1112") double destinationLng) {


}
