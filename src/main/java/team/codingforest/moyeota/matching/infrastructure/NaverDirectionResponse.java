package team.codingforest.moyeota.matching.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 *  traoptimal (기본값)	실시간 최적
 *  trafast	실시간 빠른 길	소요 시간 최소.
 *  tracomfort	실시간 편한 길
 *  traavoidtoll	무료 우선	유료도로 회피.
 *  traavoidcaronly	자동차 전용 도로 회피
 *      route는 여러 선택지가 있기에 Map으로 받는다.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverDirectionResponse(int code, String message, Map<String, List<RoutePath>> route) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoutePath(Summary summary, List<List<Double>> path) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(int distance, long duration, int taxiFare) {}
}
