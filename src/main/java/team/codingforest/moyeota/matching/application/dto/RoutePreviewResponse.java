package team.codingforest.moyeota.matching.application.dto;

import team.codingforest.moyeota.matching.domain.RouteEstimate;

public record RoutePreviewResponse(Integer estimateFare, Integer estimateTime, String path) {

    public static RoutePreviewResponse from(RouteEstimate r) {
        return new RoutePreviewResponse(r.estimateFare(), r.estimateTime(), r.path());
    }
}
