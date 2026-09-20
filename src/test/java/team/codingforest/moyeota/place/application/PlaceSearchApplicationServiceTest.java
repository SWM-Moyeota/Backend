package team.codingforest.moyeota.place.application;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.application.dto.PlaceSearchListResponse;
import team.codingforest.moyeota.place.application.dto.PlaceSearchResponse;
import team.codingforest.moyeota.place.domain.Place;
import team.codingforest.moyeota.place.domain.PlaceSearchCache;
import team.codingforest.moyeota.place.domain.PlaceSearcher;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceSearchApplicationServiceTest {

    private static final Place 강남역 = new Place("강남역 2호선", "서울 강남구 강남대로 지하 396", 37.4980, 127.0280);
    private static final Place 교보문고 = new Place("교보문고 강남점", "서울 서초구 강남대로 465", 37.5037, 127.0241);

    /** 메모리 캐시 - Redis 없이 서비스의 캐시 사용 순서를 검증 */
    static class FakeCache implements PlaceSearchCache {
        final Map<String, List<Place>> store = new HashMap<>();

        @Override
        public Optional<List<Place>> find(String key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public void save(String key, List<Place> places) {
            store.put(key, places);
        }
    }

    private static final PlaceSearcher 호출되면_안_됨 = query -> { throw new AssertionError("외부 검색이 호출되면 안 된다"); };

    @Test
    void 검색_결과를_응답으로_변환한다() {
        PlaceSearchApplicationService service =
                new PlaceSearchApplicationService(query -> List.of(강남역, 교보문고), new FakeCache());

        PlaceSearchListResponse result = service.search("강남역");

        assertThat(result.list()).hasSize(2);
        assertThat(result.list())
                .extracting(PlaceSearchResponse::name)
                .containsExactly("강남역 2호선", "교보문고 강남점");
    }

    @Test
    void 빈_검색어로는_검색할_수_없다() {
        // 검색창이 비어있는데 호출되면 카카오 API 호출량만 낭비된다 - 외부 호출 전에 차단
        PlaceSearchApplicationService service = new PlaceSearchApplicationService(호출되면_안_됨, new FakeCache());

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
                new PlaceSearchApplicationService(query -> List.of(), new FakeCache());

        PlaceSearchListResponse result = service.search("존재하지않는곳아무데나");

        assertThat(result.list()).isEmpty();
    }

    @Test
    void 같은_검색어를_다시_검색하면_외부_API_를_호출하지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        PlaceSearchApplicationService service = new PlaceSearchApplicationService(
                query -> { calls.incrementAndGet(); return List.of(강남역); }, new FakeCache());

        service.search("강남역");
        PlaceSearchListResponse second = service.search("강남역");

        assertThat(calls).hasValue(1);
        assertThat(second.list()).extracting(PlaceSearchResponse::name).containsExactly("강남역 2호선");
    }

    @Test
    void 캐시에_있으면_외부_API_없이_바로_응답한다() {
        FakeCache cache = new FakeCache();
        cache.save("강남역", List.of(교보문고));
        PlaceSearchApplicationService service = new PlaceSearchApplicationService(호출되면_안_됨, cache);

        assertThat(service.search("강남역").list())
                .extracting(PlaceSearchResponse::name).containsExactly("교보문고 강남점");
    }

    @Test
    void 공백과_대소문자가_달라도_같은_캐시_키를_쓴다() {
        AtomicInteger calls = new AtomicInteger();
        FakeCache cache = new FakeCache();
        PlaceSearchApplicationService service = new PlaceSearchApplicationService(
                query -> { calls.incrementAndGet(); return List.of(강남역); }, cache);

        service.search("  Starbucks   강남역 ");
        service.search("starbucks 강남역");

        assertThat(calls).hasValue(1);
        assertThat(cache.store).containsOnlyKeys("starbucks 강남역");
    }

    @Test
    void 빈_결과도_캐시해서_같은_오타_검색이_반복_호출되지_않게_한다() {
        AtomicInteger calls = new AtomicInteger();
        PlaceSearchApplicationService service = new PlaceSearchApplicationService(
                query -> { calls.incrementAndGet(); return List.of(); }, new FakeCache());

        service.search("ㅁㄴㅇㄹ");
        service.search("ㅁㄴㅇㄹ");

        assertThat(calls).hasValue(1);
    }

    @Test
    void 외부_검색이_실패하면_캐시에_남기지_않는다() {
        FakeCache cache = new FakeCache();
        PlaceSearchApplicationService service = new PlaceSearchApplicationService(
                query -> { throw new BusinessException(PlaceErrorCode.PLACE_SEARCH_FAILED); }, cache);

        assertThatThrownBy(() -> service.search("강남역")).isInstanceOf(BusinessException.class);

        assertThat(cache.store).isEmpty();
    }
}
