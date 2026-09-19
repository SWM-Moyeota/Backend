package team.codingforest.moyeota.place.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.application.dto.PlaceSearchListResponse;
import team.codingforest.moyeota.place.application.dto.PlaceSearchResponse;
import team.codingforest.moyeota.place.domain.Place;
import team.codingforest.moyeota.place.domain.PlaceSearcher;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;
import team.codingforest.moyeota.searchhistory.service.SearchHistoryService;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceSearchApplicationServiceTest {

    private static final Long 유저 = 1L;
    private static final Place 강남역 = new Place("강남역 2호선", "서울 강남구 강남대로 지하 396", 37.4980, 127.0280);
    private static final Place 교보문고 = new Place("교보문고 강남점", "서울 서초구 강남대로 465", 37.5037, 127.0241);

    /** DB 없이 record 호출만 받아 적는다 */
    static class RecordingSearchHistoryService extends SearchHistoryService {
        final List<String> recorded = new ArrayList<>();
        RuntimeException failWith;

        RecordingSearchHistoryService() { super(null, null); }

        @Override
        public void record(Long userId, String query) {
            if (failWith != null) throw failWith;
            recorded.add(userId + ":" + query);
        }
    }

    private RecordingSearchHistoryService histories;

    @BeforeEach
    void setUp() {
        histories = new RecordingSearchHistoryService();
    }

    private PlaceSearchApplicationService serviceWith(PlaceSearcher searcher) {
        return new PlaceSearchApplicationService(searcher, histories);
    }

    @Test
    void 검색_결과를_응답으로_변환한다() {
        PlaceSearchApplicationService service = serviceWith(query -> List.of(강남역, 교보문고));

        PlaceSearchListResponse result = service.search("강남역", 유저);

        assertThat(result.list()).hasSize(2);
        assertThat(result.list())
                .extracting(PlaceSearchResponse::name)
                .containsExactly("강남역 2호선", "교보문고 강남점");
    }

    @Test
    void 검색하면_공백을_정리한_검색어가_기록된다() {
        PlaceSearchApplicationService service = serviceWith(query -> List.of(강남역));

        service.search("  강남역 ", 유저);

        assertThat(histories.recorded).containsExactly("1:강남역");
    }

    @Test
    void 빈_검색어로는_검색할_수_없다() {
        // 검색창이 비어있는데 호출되면 카카오 API 호출량만 낭비된다 - 외부 호출 전에 차단
        PlaceSearchApplicationService service =
                serviceWith(query -> { throw new AssertionError("외부 검색이 호출되면 안 된다"); });

        assertThatThrownBy(() -> service.search("   ", 유저))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.SEARCH_QUERY_EMPTY);
        assertThatThrownBy(() -> service.search(null, 유저))
                .isInstanceOf(BusinessException.class);
        assertThat(histories.recorded).isEmpty();
    }

    @Test
    void 너무_긴_검색어는_400() {
        PlaceSearchApplicationService service =
                serviceWith(query -> { throw new AssertionError("외부 검색이 호출되면 안 된다"); });

        assertThatThrownBy(() -> service.search("가".repeat(101), 유저))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.SEARCH_QUERY_TOO_LONG);
        assertThat(histories.recorded).isEmpty();
    }

    @Test
    void 외부_검색이_실패하면_기록하지_않는다() {
        PlaceSearchApplicationService service =
                serviceWith(query -> { throw new BusinessException(PlaceErrorCode.PLACE_SEARCH_FAILED); });

        assertThatThrownBy(() -> service.search("강남역", 유저)).isInstanceOf(BusinessException.class);
        assertThat(histories.recorded).isEmpty();
    }

    @Test
    void 기록_저장이_실패해도_검색_결과는_돌려준다() {
        histories.failWith = new IllegalStateException("DB 연결 실패");
        PlaceSearchApplicationService service = serviceWith(query -> List.of(강남역));

        PlaceSearchListResponse result = service.search("강남역", 유저);

        assertThat(result.list()).hasSize(1);
    }

    @Test
    void 검색_결과가_없으면_빈_목록을_반환한다() {
        PlaceSearchApplicationService service = serviceWith(query -> List.of());

        PlaceSearchListResponse result = service.search("존재하지않는곳아무데나", 유저);

        assertThat(result.list()).isEmpty();
    }
}
