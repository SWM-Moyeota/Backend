package team.codingforest.moyeota.user.sanction.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  노쇼 제재 계산. 노쇼는 건마다 약속 시간부터 30일 동안 세고, 제한은 약속 시간부터 지금 세는 노쇼 수 × 24시간이다
 */
class NoShowSanctionUnitTest {
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T08:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-03T09:00:00Z");

    private static NoShow noShowAt(Instant meetingAt) {
        return new NoShow(meetingAt, null);
    }

    // ───────────────────────── count ─────────────────────────

    @DisplayName("count - 무효로 돌린 노쇼와 약속 시간에서 30일이 지난 노쇼는 빼고 센다")
    @Test
    void count_excludesVoidedAndExpired() {

        /*
         * settings
         */
        List<NoShow> noShows = List.of(
                noShowAt(NOW.minus(Duration.ofDays(1))),
                noShowAt(NOW.minus(Duration.ofDays(29)).minus(Duration.ofHours(23))),
                noShowAt(NOW.minus(Duration.ofDays(30))),
                new NoShow(NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1))));

        /*
         * test
         */
        int count = NoShowSanction.count(noShows, NOW);

        /*
         * verify
         */
        assertThat(count).isEqualTo(2);
    }

    @DisplayName("count - 노쇼가 없으면 0")
    @Test
    void count_none() {

        /*
         * test
         */
        int count = NoShowSanction.count(List.of(), NOW);

        /*
         * verify
         */
        assertThat(count).isZero();
    }

    @DisplayName("count - 14일 간격 노쇼 두 건은 첫 건이 만료되기 전엔 2, 첫 건이 만료되면 1, 둘째 건까지 만료되면 0")
    @ParameterizedTest(name = "첫 노쇼 약속 시간 + {0} → {1}건")
    @CsvSource({
            "P14D, 2",
            "PT719H59M59S, 2",
            "P30D, 1",
            "P43D, 1",
            "P44D, 0",
            "P60D, 0"
    })
    void count_twoNoShows14DaysApart(String elapsed, int expected) {

        /*
         * settings
         */
        Instant first = MEETING_AT;
        Instant second = MEETING_AT.plus(Duration.ofDays(14));
        List<NoShow> noShows = List.of(noShowAt(first), noShowAt(second));

        /*
         * test
         */
        int count = NoShowSanction.count(noShows, first.plus(Duration.parse(elapsed)));

        /*
         * verify
         */
        assertThat(count).isEqualTo(expected);
    }

    // ───────────────────────── record ─────────────────────────

    @DisplayName("record - 처음 노쇼면 약속 시간부터 24시간 제한")
    @Test
    void record_first() {

        /*
         * test
         */
        NoShowSanction next = NoShowSanction.NONE.record(MEETING_AT, List.of(noShowAt(MEETING_AT)), NOW);

        /*
         * verify
         */
        assertThat(next.restrictedUntil()).isEqualTo(MEETING_AT.plus(Duration.ofHours(24)));
    }

    @DisplayName("record - 이번 노쇼까지 지금 세는 노쇼가 n 건이면 약속 시간부터 n × 24시간 제한")
    @ParameterizedTest(name = "{0}건 → {1}시간")
    @CsvSource({
            "1, 24",
            "2, 48",
            "3, 72",
            "5, 120"
    })
    void record_restrictionByCount(int count, int hours) {

        /*
         * settings
         */
        List<NoShow> noShows = new ArrayList<>();
        for (int i = 0; i < count; i++) noShows.add(noShowAt(MEETING_AT.minus(Duration.ofDays(i))));

        /*
         * test
         */
        NoShowSanction next = NoShowSanction.NONE.record(MEETING_AT, noShows, NOW);

        /*
         * verify
         */
        assertThat(next.restrictedUntil()).isEqualTo(MEETING_AT.plus(Duration.ofHours(hours)));
    }

    @DisplayName("record - 무효로 돌린 노쇼와 30일이 지난 노쇼는 제한 길이에 들어가지 않는다")
    @Test
    void record_ignoresVoidedAndExpired() {

        /*
         * settings
         */
        List<NoShow> noShows = List.of(
                noShowAt(MEETING_AT),
                noShowAt(NOW.minus(Duration.ofDays(30))),
                new NoShow(NOW.minus(Duration.ofDays(3)), NOW.minus(Duration.ofDays(2))));

        /*
         * test
         */
        NoShowSanction next = NoShowSanction.NONE.record(MEETING_AT, noShows, NOW);

        /*
         * verify
         */
        assertThat(next.restrictedUntil()).isEqualTo(MEETING_AT.plus(Duration.ofHours(24)));
    }

    @DisplayName("record - 이미 더 늦게 끝나는 제한이 걸려 있으면 그쪽을 남긴다")
    @Test
    void record_keepsLaterRestriction() {

        /*
         * settings
         */
        Instant later = MEETING_AT.plus(Duration.ofDays(10));
        NoShowSanction sanction = new NoShowSanction(later);
        List<NoShow> noShows = List.of(noShowAt(MEETING_AT.minus(Duration.ofDays(1))), noShowAt(MEETING_AT));

        /*
         * test
         */
        NoShowSanction next = sanction.record(MEETING_AT, noShows, NOW);

        /*
         * verify
         */
        assertThat(next.restrictedUntil()).isEqualTo(later);
    }

    @DisplayName("record - 걸려 있던 제한이 새 제한보다 이르면 새 제한으로 늘린다")
    @Test
    void record_extendsEarlierRestriction() {

        /*
         * settings
         */
        NoShowSanction sanction = new NoShowSanction(MEETING_AT.plus(Duration.ofHours(1)));
        List<NoShow> noShows = List.of(noShowAt(MEETING_AT.minus(Duration.ofDays(1))), noShowAt(MEETING_AT));

        /*
         * test
         */
        NoShowSanction next = sanction.record(MEETING_AT, noShows, NOW);

        /*
         * verify
         */
        assertThat(next.restrictedUntil()).isEqualTo(MEETING_AT.plus(Duration.ofHours(48)));
    }

    @DisplayName("record - 약속 시간이 없으면 NullPointerException")
    @Test
    void record_withoutMeetingAt() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> NoShowSanction.NONE.record(null, List.of(), NOW));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    // ───────────────────────── cancel ─────────────────────────

    @DisplayName("cancel - 지금 걸린 제한을 푼다")
    @Test
    void cancel() {

        /*
         * settings
         */
        NoShowSanction sanction = new NoShowSanction(MEETING_AT.plus(Duration.ofHours(48)));

        /*
         * test
         */
        NoShowSanction next = sanction.cancel();

        /*
         * verify
         */
        assertThat(next.restrictedUntil()).isNull();
        assertThat(next.activeRestrictionUntil(NOW)).isEmpty();
    }

    // ───────────────────────── activeRestrictionUntil ─────────────────────────

    @DisplayName("activeRestrictionUntil - 제한 끝 시각이 지금보다 뒤면 그 시각을 준다")
    @Test
    void activeRestrictionUntil_restricted() {

        /*
         * settings
         */
        Instant until = NOW.plusSeconds(1);
        NoShowSanction sanction = new NoShowSanction(until);

        /*
         * test & verify
         */
        assertThat(sanction.activeRestrictionUntil(NOW)).contains(until);
    }

    @DisplayName("activeRestrictionUntil - 제한 끝 시각이 지났거나 같으면 empty")
    @Test
    void activeRestrictionUntil_expired() {

        /*
         * settings
         */
        NoShowSanction ended = new NoShowSanction(NOW);
        NoShowSanction past = new NoShowSanction(NOW.minusSeconds(1));

        /*
         * test & verify
         */
        assertThat(ended.activeRestrictionUntil(NOW)).isEmpty();
        assertThat(past.activeRestrictionUntil(NOW)).isEmpty();
        assertThat(NoShowSanction.NONE.activeRestrictionUntil(NOW)).isEmpty();
    }
}
