package team.codingforest.moyeota.place.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.domain.Address;
import team.codingforest.moyeota.place.domain.RegionSearcher;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

import java.util.Optional;

@Slf4j
@Component
public class NaverReverseGeoCodingClient implements RegionSearcher {

    private final RestClient naverRestClient;

    public NaverReverseGeoCodingClient(RestClient naverRestClient) {
        this.naverRestClient = naverRestClient;
    }

    @Override
    public Optional<Address> find(double latitude, double longitude) {
        NaverReverseGeocodeResponse response;

        try {
            response = naverRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/map-reversegeocode/v2/gc")
                            .queryParam("coords", longitude + "," + latitude)
                            .queryParam("orders", "roadaddr,addr")
                            .queryParam("output", "json")

                            .build())
                    .retrieve()
                    .body(NaverReverseGeocodeResponse.class);
        } catch (RestClientException e) {
            log.warn("네이버 역지오코딩 실패 lat={}, lng={}", latitude, longitude, e);
            throw new BusinessException(PlaceErrorCode.REVERSE_GEOCODE_FAILED);
        }

        if(response == null || response.status().code() != 0 || response.results() == null || response.results().isEmpty()) {
            return Optional.empty();
        }

        String road = null;
        String jibun = null;

        for(NaverReverseGeocodeResponse.Result result : response.results()) {
            if("roadaddr".equals(result.name())) road = assembleRoad(result);
            if("addr".equals(result.name())) jibun = assembleJibun(result);
        }

        return Optional.of(new Address(road, jibun));
    }

    private String assembleRoad(NaverReverseGeocodeResponse.Result r) {
        StringBuilder sb = new StringBuilder();
        append(sb, r.region().area1().name());
        append(sb, r.region().area2().name());
        append(sb, r.land().name());
        append(sb, number(r.land().number1(), r.land().number2()));
        if(r.land().addition0() != null && r.land().addition0().value() != null && !r.land().addition0().value().isBlank()) {
            append(sb, r.land().addition0().value());
        }
        return sb.toString();
    }

    private String assembleJibun(NaverReverseGeocodeResponse.Result r) {
        StringBuilder sb = new StringBuilder();
        append(sb, r.region().area1().name());
        append(sb, r.region().area2().name());
        append(sb, r.region().area3().name());
        append(sb, r.region().area4().name());
        append(sb, number(r.land().number1(), r.land().number2()));
        return sb.toString();
    }

    private String number(String n1, String n2) {
        if(n1 == null || n1.isBlank()) return null;
        return (n2 == null || n2.isBlank()) ? n1 : n1 + "-" + n2;
    }

    private void append(StringBuilder sb, String part) {
        if(part == null || part.isBlank()) return;
        if(!sb.isEmpty()) sb.append(" ");
        sb.append(part);
    }
}
