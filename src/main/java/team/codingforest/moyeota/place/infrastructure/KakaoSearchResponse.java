package team.codingforest.moyeota.place.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)     // 카카오 API에서 주는 필드 중 안 쓰는 필드는 무시
public record KakaoSearchResponse(List<KakaoPlaceDocument> documents) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoPlaceDocument(@JsonProperty("place_name") String placeName,
                                     @JsonProperty("road_address_name") String roadAddressName,
                                     String x,
                                     String y) {}
}
