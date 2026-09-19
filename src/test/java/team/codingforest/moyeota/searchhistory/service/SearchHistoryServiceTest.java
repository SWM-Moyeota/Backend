package team.codingforest.moyeota.searchhistory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.searchhistory.dto.SearchHistoryResponse;
import team.codingforest.moyeota.searchhistory.exception.SearchHistoryErrorCode;
import team.codingforest.moyeota.searchhistory.repository.SearchHistoryRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** H2 위에서 실제 쿼리까지 태운다 - 중복 처리·정렬·개수 제한이 JPA 쿼리와 맞물려 도는지가 관심사 */
@DataJpaTest
@Import({SearchHistoryService.class, SearchHistoryServiceTest.FixedClockConfig.class})
class SearchHistoryServiceTest {

    private static final Long 유저 = 1L;
    private static final Long 다른유저 = 2L;
    private static final Instant 시작 = Instant.parse("2026-09-20T09:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return new MutableClock(시작);
        }
    }

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 Clock. 같은 검색어를 "나중에" 다시 검색한 상황을 만들 때 쓴다 */
    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void advance(Duration duration) { now = now.plus(duration); }

        void reset(Instant instant) { now = instant; }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Autowired SearchHistoryService service;
    @Autowired SearchHistoryRepository repository;
    @Autowired Clock clock;

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    /** Clock 빈은 컨텍스트에 하나뿐이라 앞 테스트에서 돌린 시간이 남는다 - 매번 되돌린다 */
    @BeforeEach
    void resetClock() {
        clock().reset(시작);
    }

    private List<String> queries(Long userId) {
        return service.getList(userId).histories().stream().map(SearchHistoryResponse::query).toList();
    }

    @Test
    void 검색하면_기록이_저장된다() {
        service.record(유저, "강남역");

        List<SearchHistoryResponse> result = service.getList(유저).histories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).query()).isEqualTo("강남역");
        assertThat(result.get(0).createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 20, 9, 0));
    }

    @Test
    void 최근에_검색한_것이_위로_온다() {
        service.record(유저, "강남역");
        clock().advance(Duration.ofMinutes(1));
        service.record(유저, "홍대입구");
        clock().advance(Duration.ofMinutes(1));
        service.record(유저, "잠실");

        assertThat(queries(유저)).containsExactly("잠실", "홍대입구", "강남역");
    }

    @Test
    void 같은_검색어를_다시_검색하면_중복_없이_맨_위로_올라온다() {
        service.record(유저, "강남역");
        clock().advance(Duration.ofMinutes(1));
        service.record(유저, "홍대입구");
        clock().advance(Duration.ofMinutes(1));
        service.record(유저, "강남역");

        assertThat(queries(유저)).containsExactly("강남역", "홍대입구");
        assertThat(repository.count()).isEqualTo(2);
        assertThat(service.getList(유저).histories().get(0).createdAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 20, 9, 2));
    }

    @Test
    void 앞뒤_공백만_다른_검색어는_같은_검색어로_취급한다() {
        service.record(유저, "강남역");
        service.record(유저, "  강남역 ");

        assertThat(queries(유저)).containsExactly("강남역");
    }

    @Test
    void 최근_10개만_남기고_오래된_기록은_지운다() {
        for (int i = 1; i <= 12; i++) {
            service.record(유저, "검색어" + i);
            clock().advance(Duration.ofSeconds(1));
        }

        List<String> result = queries(유저);

        assertThat(result).hasSize(SearchHistoryService.MAX_HISTORY_SIZE);
        assertThat(result.get(0)).isEqualTo("검색어12");
        assertThat(result).doesNotContain("검색어1", "검색어2");
    }

    @Test
    void 빈_검색어와_너무_긴_검색어는_기록하지_않는다() {
        // 검색 API 가 먼저 400 으로 거르지만, 다른 경로로 들어와도 DB 제약(길이 100)에 걸려 터지면 안 된다
        service.record(유저, "   ");
        service.record(유저, null);
        service.record(유저, "가".repeat(101));

        assertThat(queries(유저)).isEmpty();
    }

    @Test
    void 다른_유저의_기록은_보이지_않는다() {
        service.record(유저, "강남역");
        service.record(다른유저, "홍대입구");

        assertThat(queries(유저)).containsExactly("강남역");
        assertThat(queries(다른유저)).containsExactly("홍대입구");
    }

    @Test
    void 내_기록_하나를_삭제한다() {
        service.record(유저, "강남역");
        clock().advance(Duration.ofMinutes(1));
        service.record(유저, "홍대입구");
        Long 강남역 = service.getList(유저).histories().get(1).id();

        service.delete(유저, 강남역);

        assertThat(queries(유저)).containsExactly("홍대입구");
    }

    @Test
    void 남의_기록은_삭제할_수_없다() {
        service.record(다른유저, "홍대입구");
        Long 남의기록 = service.getList(다른유저).histories().get(0).id();

        assertThatThrownBy(() -> service.delete(유저, 남의기록))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SearchHistoryErrorCode.SEARCH_HISTORY_NOT_FOUND);
        assertThat(queries(다른유저)).containsExactly("홍대입구");
    }

    @Test
    void 없는_기록을_삭제하면_404() {
        assertThatThrownBy(() -> service.delete(유저, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SearchHistoryErrorCode.SEARCH_HISTORY_NOT_FOUND);
    }

    @Test
    void 전체_삭제는_내_기록만_지운다() {
        service.record(유저, "강남역");
        service.record(유저, "홍대입구");
        service.record(다른유저, "잠실");

        service.deleteAll(유저);

        assertThat(queries(유저)).isEmpty();
        assertThat(queries(다른유저)).containsExactly("잠실");
    }
}
