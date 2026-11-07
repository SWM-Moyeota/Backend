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
 *  노쇼 1건의 만료. 약속 시간부터 30일 동안만 세고, 정확히 30일이 된 순간부터는 세지 않는다
 */
class NoShowUnitTest {
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T08:00:00Z");

    @DisplayName("countsAt - 약속 시간에서 30일이 되기 1마이크로초 전까지 세고, 30일을 넘으면 세지 않는다")
    @ParameterizedTest(name = "약속 시간 + {0} → {1}")
    @CsvSource({
            "PT0S, true",
            "P1D, true",
            "PT719H59M59.999999S, true",
            "PT720H0.000001S, false",
            "P60D, false"
    })
    void countsAt_window(String elapsed, boolean expected) {

        /*
         * settings
         */
        NoShow noShow = new NoShow(MEETING_AT, null);

        /*
         * test
         */
        boolean counts = noShow.countsAt(MEETING_AT.plus(Duration.parse(elapsed)));

        /*
         * verify
         */
        assertThat(counts).isEqualTo(expected);
    }

    @DisplayName("countsAt - 29일 23시간이 지난 노쇼는 센다")
    @Test
    void countsAt_29days23hours() {

        /*
         * settings
         */
        NoShow noShow = new NoShow(MEETING_AT, null);

        /*
         * test
         */
        boolean counts = noShow.countsAt(MEETING_AT.plus(Duration.ofDays(29)).plus(Duration.ofHours(23)));

        /*
         * verify
         */
        assertThat(counts).isTrue();
    }

    @DisplayName("countsAt - 정확히 30일이 지난 노쇼는 세지 않는다")
    @Test
    void countsAt_exactly30days() {

        /*
         * settings
         */
        NoShow noShow = new NoShow(MEETING_AT, null);

        /*
         * test
         */
        boolean counts = noShow.countsAt(MEETING_AT.plus(Duration.ofDays(30)));

        /*
         * verify
         */
        assertThat(counts).isFalse();
    }

    @DisplayName("countsAt - 무효로 돌린 노쇼는 30일 안이어도 세지 않는다")
    @Test
    void countsAt_voided() {

        /*
         * settings
         */
        NoShow noShow = new NoShow(MEETING_AT, MEETING_AT.plus(Duration.ofHours(2)));

        /*
         * test
         */
        boolean counts = noShow.countsAt(MEETING_AT.plus(Duration.ofDays(1)));

        /*
         * verify
         */
        assertThat(counts).isFalse();
    }

    @DisplayName("countsAt - 약속 시간이 지금보다 뒤여도(서버 시계 어긋남) 센다")
    @Test
    void countsAt_meetingAfterNow() {

        /*
         * settings
         */
        NoShow noShow = new NoShow(MEETING_AT, null);

        /*
         * test
         */
        boolean counts = noShow.countsAt(MEETING_AT.minusSeconds(1));

        /*
         * verify
         */
        assertThat(counts).isTrue();
    }

    @DisplayName("expiredUntil - 지금에서 30일 전이다")
    @Test
    void expiredUntil() {

        /*
         * test
         */
        Instant until = NoShow.expiredUntil(MEETING_AT);

        /*
         * verify
         */
        assertThat(until).isEqualTo(MEETING_AT.minus(Duration.ofDays(30)));
    }

    @DisplayName("생성 - 약속 시간이 없으면 NullPointerException")
    @Test
    void create_withoutMeetingAt() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> new NoShow(null, null));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }
}
