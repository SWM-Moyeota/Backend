package team.codingforest.moyeota.place.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KakaoSearchResponse(List<KakaoPlaceDocument> documents) {

    public record KakaoPlaceDocument(@JsonProperty("place_name") String placeName,
                                     @JsonProperty("road_address_name") String roadAddressName,
                                     String x,
                                     String y) {}
}
