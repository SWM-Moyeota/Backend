package team.codingforest.moyeota.place.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

import static org.assertj.core.api.Assertions.*;

class KakaoPlaceClientTest {

    @Test
    void 카카오_장애는_규격_안의_검색_실패_예외로_변환된다() {
        // 외부 API가 죽었을 때 정체불명 500이 아니라 PLACE_SEARCH_FAILED(502)로 나가야
        // 앱이 "잠시 후 다시 시도" 안내를 띄울 수 있다 - 접속 불가능한 주소로 장애를 재현
        KakaoPlaceClient client = new KakaoPlaceClient(
                RestClient.builder().baseUrl("http://127.0.0.1:1").build());

        assertThatThrownBy(() -> client.search("강남역"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.PLACE_SEARCH_FAILED);
    }
}
