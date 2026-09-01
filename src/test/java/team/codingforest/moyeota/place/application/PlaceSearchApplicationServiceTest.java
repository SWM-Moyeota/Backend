package team.codingforest.moyeota.place.application;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.place.application.dto.PlaceSearchListResponse;
import team.codingforest.moyeota.place.application.dto.PlaceSearchResponse;
import team.codingforest.moyeota.place.domain.Place;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PlaceSearchApplicationServiceTest {

    private static final Place 강남역 = new Place("강남역 2호선", "서울 강남구 강남대로 지하 396", 37.4980, 127.0280);
    private static final Place 교보문고 = new Place("교보문고 강남점", "서울 서초구 강남대로 465", 37.5037, 127.0241);

    @Test
    void 검색_결과를_응답으로_변환한다() {
        PlaceSearchApplicationService service =
                new PlaceSearchApplicationService(query -> List.of(강남역, 교보문고));

        PlaceSearchListResponse result = service.search("강남역");

        assertThat(result.list()).hasSize(2);
        assertThat(result.list())
                .extracting(PlaceSearchResponse::name)
                .containsExactly("강남역 2호선", "교보문고 강남점");
    }

    @Test
    void 빈_검색어로는_검색할_수_없다() {
        // 검색창이 비어있는데 호출되면 카카오 API 호출량만 낭비된다 - 외부 호출 전에 차단
        PlaceSearchApplicationService service =
                new PlaceSearchApplicationService(query -> { throw new AssertionError("외부 검색이 호출되면 안 된다"); });

        assertThatThrownBy(() -> service.search("   "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.SEARCH_QUERY_EMPTY);
        assertThatThrownBy(() -> service.search(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 검색_결과가_없으면_빈_목록을_반환한다() {
        PlaceSearchApplicationService service =
                new PlaceSearchApplicationService(query -> List.of());

        PlaceSearchListResponse result = service.search("존재하지않는곳아무데나");

        assertThat(result.list()).isEmpty();
    }
}
