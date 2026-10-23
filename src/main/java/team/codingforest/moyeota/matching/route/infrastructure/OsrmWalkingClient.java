package team.codingforest.moyeota.matching.route.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.route.domain.Coordinate;
import team.codingforest.moyeota.matching.route.domain.WalkRouteFinder;
import team.codingforest.moyeota.matching.route.domain.WalkingEstimate;
import team.codingforest.moyeota.matching.route.dto.OsrmTableResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class OsrmWalkingClient implements WalkRouteFinder {

    private final RestClient osrmRestClient;

    private static final String PATH = "/table/v1/foot/";
    private static final String SOURCES = "sources";
    private static final String DESTINATIONS = "destinations";
    private static final String ANNOTATIONS = "annotations";
    private static final String DURATION = "duration";

    @Override
    public List<WalkingEstimate> fromTo(Coordinate from, List<Coordinate> to) {
        if (to.isEmpty()) return List.of();

        List<Coordinate> coords = new ArrayList<>();
        coords.add(from);
        coords.addAll(to);

        OsrmTableResponse response = table(coords, "0", indexes(1, coords.size()));

        return IntStream.range(0, to.size())
                .mapToObj(i -> toEstimate(response, 0, i))
                .toList();
    }

    @Override
    public List<WalkingEstimate> toFrom(Coordinate to, List<Coordinate> from) {
        if (from.isEmpty()) return List.of();

        List<Coordinate> coords = new ArrayList<>(from);
        coords.add(to);

        OsrmTableResponse response = table(coords, indexes(0, from.size()), String.valueOf(from.size()));

        return IntStream.range(0, from.size())
                .mapToObj(i -> toEstimate(response, i, 0))
                .toList();
    }

    private OsrmTableResponse table(List<Coordinate> coords, String source, String destinations) {
        String path = coords.stream().map(c -> c.lng() + "," + c.lat())
                .collect(Collectors.joining(";"));

        OsrmTableResponse response;

        try {
            response = osrmRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(PATH + path)
                            .queryParam(SOURCES, source)
                            .queryParam(DESTINATIONS, destinations)
                            .queryParam(ANNOTATIONS, DURATION)
                            .build())
                    .retrieve()
                    .body(OsrmTableResponse.class);
        } catch (RestClientException e) {
            log.warn("OSRM 도보 table 조회 실패 points={}", coords.size(), e);
            throw new BusinessException(MatchingErrorCode.ROUTE_SEARCH_FAILED);
        }

        if (response == null || !"Ok".equals(response.code()) || response.durations() == null) {
            log.warn("OSRM 도보 table 응답 이상 code={}", response == null ? "응답 없음" : response.code());
            throw new BusinessException(MatchingErrorCode.ROUTE_SEARCH_FAILED);
        }

        return response;
    }

    private String indexes(int fromInclusive, int toExclusive) {
        return IntStream.range(fromInclusive, toExclusive)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(";"));
    }

    private WalkingEstimate toEstimate(OsrmTableResponse response, int r, int c) {
        Double duration = response.durations().get(r).get(c);

        if (duration == null) return null;
        return new WalkingEstimate((int) Math.round(duration));
    }
}
