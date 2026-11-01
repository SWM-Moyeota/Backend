package team.codingforest.moyeota.place.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;
import team.codingforest.moyeota.place.search.domain.Place;
import team.codingforest.moyeota.place.search.domain.PlaceSearcher;
import team.codingforest.moyeota.place.search.dto.PlaceSearchListResponse;
import team.codingforest.moyeota.place.search.dto.PlaceSearchResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PlaceSearchServiceUnitTest {
    private static final String QUERY = "강남역";

    @Mock
    private PlaceSearcher placeSearcher;
    @InjectMocks
    private PlaceSearchService service;

    @DisplayName("search - 검색 결과를 순서 그대로 필드까지 옮겨 반환")
    @Test
    void search() {

        /*
         * settings
         */
        doReturn(List.of(
                new Place("강남역 2호선", "서울 강남구 강남대로 396", 37.4979, 127.0276),
                new Place("강남역 신분당선", "서울 강남구 강남대로 지하 396", 37.4966, 127.0285)
        )).when(placeSearcher).search(eq(QUERY));

        /*
         * test
         */
        PlaceSearchListResponse response = service.search(QUERY);

        /*
         * verify
         */
        assertThat(response.list()).containsExactly(
                new PlaceSearchResponse("강남역 2호선", "서울 강남구 강남대로 396", 37.4979, 127.0276),
                new PlaceSearchResponse("강남역 신분당선", "서울 강남구 강남대로 지하 396", 37.4966, 127.0285)
        );
    }

    @DisplayName("search - 검색어 앞뒤 공백은 지우지 않고 그대로 넘긴다")
    @Test
    void search_queryPassedAsIs() {

        /*
         * settings
         */
        doReturn(List.of()).when(placeSearcher).search(eq(" 강남역 "));

        /*
         * test
         */
        PlaceSearchListResponse response = service.search(" 강남역 ");

        /*
         * verify
         */
        assertThat(response.list()).isEmpty();
    }

    @DisplayName("search - 도로명이 없는 결과도 null 그대로 옮긴다")
    @Test
    void search_noRoadName() {

        /*
         * settings
         */
        doReturn(List.of(new Place("강남역 2호선", null, 37.4979, 127.0276))).when(placeSearcher).search(eq(QUERY));

        /*
         * test
         */
        PlaceSearchListResponse response = service.search(QUERY);

        /*
         * verify
         */
        assertThat(response.list()).containsExactly(new PlaceSearchResponse("강남역 2호선", null, 37.4979, 127.0276));
    }

    @DisplayName("search - 결과가 없으면 빈 목록")
    @Test
    void search_noResult() {

        /*
         * settings
         */
        doReturn(List.of()).when(placeSearcher).search(eq(QUERY));

        /*
         * test
         */
        PlaceSearchListResponse response = service.search(QUERY);

        /*
         * verify
         */
        assertThat(response.list()).isEmpty();
    }

    @DisplayName("search - 검색어가 null 이거나 비어 있으면 외부 API 를 부르지 않고 실패")
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t", "\n", "　"})
    void search_emptyQuery(String query) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.search(query));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.SEARCH_QUERY_EMPTY);
        verifyNoInteractions(placeSearcher);
    }

    @DisplayName("search - 외부 API 실패 예외는 그대로 전파")
    @Test
    void search_searcherFailed() {

        /*
         * settings
         */
        doThrow(new BusinessException(PlaceErrorCode.PLACE_SEARCH_FAILED)).when(placeSearcher).search(eq(QUERY));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.search(QUERY));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.PLACE_SEARCH_FAILED);
    }
}
