package team.codingforest.moyeota.user.sanction.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  노쇼 제재 계산. 누적 횟수는 노쇼 없이 30일마다 1 줄고(저장하지 않고 계산), 제한은 약속 시간부터 누적 횟수 × 24시간이다
 */
class NoShowSanctionUnitTest {
    private static final Instant UPDATED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T08:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-03T09:00:00Z");

    // ───────────────────────── effectiveCount ─────────────────────────

    @DisplayName("effectiveCount - 마지막 변경에서 지난 30일 수만큼 줄인다(0·29일은 그대로, 30일에 1, 61일에 2)")
    @ParameterizedTest(name = "누적 {0}회, {1}일 뒤 → {2}회")
    @CsvSource({
            "3, 0, 3",
            "3, 29, 3",
            "3, 30, 2",
            "3, 59, 2",
            "3, 60, 1",
            "3, 61, 1",
            "1, 61, 0",
            "2, 365, 0"
    })
    void effectiveCount_decay(int count, int days, int expected) {

        /*
         * settings
         */
        NoShowSanction sanction = new NoShowSanction(count, UPDATED_AT, null);

        /*
         * test
         */
        int effective = sanction.effectiveCount(UPDATED_AT.plus(Duration.ofDays(days)));

        /*
         * verify
         */
        assertThat(effective).isEqualTo(expected);
    }

    @DisplayName("effectiveCount - 30일에서 1초 모자라면 줄지 않는다")
    @Test
    void effectiveCount_justBeforePeriod() {

        /*
         * settings
         */
        NoShowSanction sanction = new NoShowSanction(2, UPDATED_AT, null);

        /*
         * test
         */
        int effective = sanction.effectiveCount(UPDATED_AT.plus(Duration.ofDays(30)).minusSeconds(1));

        /*
         * verify
         */
        assertThat(effective).isEqualTo(2);
    }

    @DisplayName("effectiveCount - 노쇼가 없었으면(기준 시각 null) 0 이다")
    @Test
    void effectiveCount_none() {

        /*
         * test
         */
        int effective = NoShowSanction.NONE.effectiveCount(NOW);

        /*
         * verify
         */
        assertThat(effective).isZero();
    }

    @DisplayName("effectiveCount - 지금이 기준 시각보다 이르면(서버 시계 어긋남) 줄이지 않는다")
    @Test
    void effectiveCount_clockSkew() {

        /*
         * settings
         */
        NoShowSanction sanction = new NoShowSanction(2, UPDATED_AT, null);

        /*
         * test
         */
        int effective = sanction.effectiveCount(UPDATED_AT.minus(Duration.ofDays(40)));

        /*
         * verify
         */
        assertThat(effective).isEqualTo(2);
    }

    @DisplayName("생성 - 누적 횟수가 음수면 IllegalArgumentException")
    @Test
    void create_negativeCount() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> new NoShowSanction(-1, UPDATED_AT, null));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    // ───────────────────────── record ─────────────────────────

    @DisplayName("record - 처음 노쇼면 1회, 약속 시간부터 24시간 제한이고 기준 시각은 지금이다")
    @Test
    void record_first() {

        /*
         * test
         */
        NoShowSanction next = NoShowSanction.NONE.record(MEETING_AT, NOW);

        /*
         * verify
         */
        assertThat(next.count()).isEqualTo(1);
        assertThat(next.countUpdatedAt()).isEqualTo(NOW);
        assertThat(next.restrictedUntil()).isEqualTo(MEETING_AT.plus(Duration.ofHours(24)));
    }

    @DisplayName("record - 누적 2회에서 한 번 더면 3회, 약속 시간부터 72시간 제한")
    @Test
    void record_accumulates() {

        /*
         * settings
         */
        NoShowSanction sanction = new NoShowSanction(2, NOW.minus(Duration.ofDays(1)), null);

        /*
         * test
         */
        NoShowSanction next = sanction.record(MEETING_AT, NOW);

        /*
         * verify
         */
        assertThat(next.count()).isEqualTo(3);
        assertThat(next.restrictedUntil()).isEqualTo(MEETING_AT.plus(Duration.ofHours(72)));
    }

    @DisplayName("record - 감소를 반영한 횟수에 1 을 더한다(누적 3회, 61일 지남 → 1 + 1 = 2회, 48시간)")
    @Test
    void record_afterDecay() {

        /*
         * settings
         */
        NoShowSanction sanction = new NoShowSanction(3, NOW.minus(Duration.ofDays(61)), null);

        /*
         * test
         */
        NoShowSanction next = sanction.record(MEETING_AT, NOW);

        /*
         * verify
         */
        assertThat(next.count()).isEqualTo(2);
        assertThat(next.countUpdatedAt()).isEqualTo(NOW);
        assertThat(next.restrictedUntil()).isEqualTo(MEETING_AT.plus(Duration.ofHours(48)));
    }

    @DisplayName("record - 이미 더 늦게 끝나는 제한이 걸려 있으면 그쪽을 남긴다")
    @Test
    void record_keepsLaterRestriction() {

        /*
         * settings
         */
        Instant later = MEETING_AT.plus(Duration.ofDays(10));
        NoShowSanction sanction = new NoShowSanction(1, NOW.minus(Duration.ofDays(1)), later);

        /*
         * test
         */
        NoShowSanction next = sanction.record(MEETING_AT, NOW);

        /*
         * verify
         */
        assertThat(next.count()).isEqualTo(2);
        assertThat(next.restrictedUntil()).isEqualTo(later);
    }

    @DisplayName("record - 걸려 있던 제한이 새 제한보다 이르면 새 제한으로 늘린다")
    @Test
    void record_extendsEarlierRestriction() {

        /*
         * settings
         */
        Instant earlier = MEETING_AT.plus(Duration.ofHours(1));
        NoShowSanction sanction = new NoShowSanction(1, NOW.minus(Duration.ofDays(1)), earlier);

        /*
         * test
         */
        NoShowSanction next = sanction.record(MEETING_AT, NOW);

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
        Throwable thrown = catchThrowable(() -> NoShowSanction.NONE.record(null, NOW));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    // ───────────────────────── cancel ─────────────────────────

    @DisplayName("cancel - 누적 횟수를 1 줄이고 지금 걸린 제한을 푼다")
    @Test
    void cancel() {

        /*
         * settings
         */
        NoShowSanction sanction = new NoShowSanction(2, NOW.minus(Duration.ofDays(1)), MEETING_AT.plus(Duration.ofHours(48)));

        /*
         * test
         */
        NoShowSanction next = sanction.cancel(NOW);

        /*
         * verify
         */
        assertThat(next.count()).isEqualTo(1);
        assertThat(next.countUpdatedAt()).isEqualTo(NOW);
        assertThat(next.restrictedUntil()).isNull();
        assertThat(next.activeRestrictionUntil(NOW)).isEmpty();
    }

    @DisplayName("cancel - 0 아래로 내려가지 않는다")
    @Test
    void cancel_floorAtZero() {

        /*
         * test
         */
        NoShowSanction next = NoShowSanction.NONE.cancel(NOW);

        /*
         * verify
         */
        assertThat(next.count()).isZero();
        assertThat(next.restrictedUntil()).isNull();
    }

    @DisplayName("cancel - 감소가 이미 0 까지 반영됐으면 0 이다(누적 1회, 30일 지남)")
    @Test
    void cancel_afterDecay() {

        /*
         * settings
         */
        NoShowSanction sanction = new NoShowSanction(1, NOW.minus(Duration.ofDays(30)), null);

        /*
         * test
         */
        NoShowSanction next = sanction.cancel(NOW);

        /*
         * verify
         */
        assertThat(next.count()).isZero();
    }

    // ───────────────────────── activeRestrictionUntil ─────────────────────────

    @DisplayName("activeRestrictionUntil - 제한 끝 시각이 지금보다 뒤면 그 시각을 준다")
    @Test
    void activeRestrictionUntil_restricted() {

        /*
         * settings
         */
        Instant until = NOW.plusSeconds(1);
        NoShowSanction sanction = new NoShowSanction(1, NOW, until);

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
        NoShowSanction ended = new NoShowSanction(1, NOW, NOW);
        NoShowSanction past = new NoShowSanction(1, NOW, NOW.minusSeconds(1));

        /*
         * test & verify
         */
        assertThat(ended.activeRestrictionUntil(NOW)).isEmpty();
        assertThat(past.activeRestrictionUntil(NOW)).isEmpty();
        assertThat(NoShowSanction.NONE.activeRestrictionUntil(NOW)).isEmpty();
    }
}
