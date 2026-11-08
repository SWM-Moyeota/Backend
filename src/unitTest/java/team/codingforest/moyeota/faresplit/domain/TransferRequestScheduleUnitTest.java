package team.codingforest.moyeota.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  송금 요청 차례 시각(1시간 뒤, 다음 날 10시 한국 시각)과, 대상 조회 쿼리가 쓰는 기준 시각이 그 차례와 맞는지
 */
class TransferRequestScheduleUnitTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @DisplayName("dueOf - 1번째는 시작 시각, 2번째는 1시간 뒤, 3번째는 시작한 날(한국 시각) 다음 날 10시")
    @Test
    void dueOf() {

        /*
         * settings
         */
        Instant createdAt = seoul("2026-11-03T21:40:00");

        /*
         * test
         */
        List<Instant> dues = List.of(TransferRequestSchedule.dueOf(createdAt, 1), TransferRequestSchedule.dueOf(createdAt, 2),
                TransferRequestSchedule.dueOf(createdAt, 3));

        /*
         * verify
         */
        assertThat(dues).containsExactly(createdAt, seoul("2026-11-03T22:40:00"), seoul("2026-11-04T10:00:00"));
    }

    @DisplayName("dueOf - 날짜는 UTC 가 아니라 한국 시각으로 센다(한국 시각 00:30 은 UTC 로 전날이다)")
    @ParameterizedTest(name = "시작 {0} KST → 3번째 {1}")
    @CsvSource({
            "2026-11-04T00:30:00, 2026-11-05T10:00:00",
            "2026-11-04T08:59:00, 2026-11-05T10:00:00",
            "2026-11-04T09:30:00, 2026-11-05T10:00:00",
            "2026-11-04T23:59:59, 2026-11-05T10:00:00"})
    void dueOf_seoulDate(LocalDateTime createdAt, LocalDateTime third) {

        /*
         * settings
         */
        Instant created = createdAt.atZone(SEOUL).toInstant();

        /*
         * test
         */
        Instant thirdDue = TransferRequestSchedule.dueOf(created, 3);

        /*
         * verify
         */
        assertThat(thirdDue).isEqualTo(third.atZone(SEOUL).toInstant());
    }

    @DisplayName("dueOf - 하루 중 언제 시작해도 마지막 차례가 정산 만료(시작 2일 뒤) 전이다")
    @Test
    void dueOf_beforeExpiry() {

        /*
         * settings
         */
        List<Instant> createdAts = grid(seoul("2026-11-03T00:00:00"), Duration.ofMinutes(7), 2 * 24 * 60 / 7);

        /*
         * test / verify
         */
        for(Instant createdAt : createdAts) {
            Instant lastDue = TransferRequestSchedule.dueOf(createdAt, TransferRequestSchedule.LIMIT);

            assertThat(lastDue).as("created=%s", createdAt).isBefore(createdAt.plus(FareSplit.EXPIRES_AFTER));
        }
    }

    @DisplayName("dueOf - 1..3 번째 밖이면 IllegalArgumentException")
    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    void dueOf_outOfRange(int nth) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> TransferRequestSchedule.dueOf(Instant.EPOCH, nth));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("passedCount - now 까지 차례가 지난 요청 수. 차례 시각과 같으면 지난 것으로 센다")
    @ParameterizedTest(name = "{0} KST → {1}")
    @CsvSource({
            "2026-11-03T21:39:59, 0",
            "2026-11-03T21:40:00, 1",
            "2026-11-03T22:39:59, 1",
            "2026-11-03T22:40:00, 2",
            "2026-11-04T09:59:59, 2",
            "2026-11-04T10:00:00, 3",
            "2026-11-05T10:00:00, 3",
            "2026-11-20T10:00:00, 3"})
    void passedCount(LocalDateTime now, int expected) {

        /*
         * settings
         */
        Instant createdAt = seoul("2026-11-03T21:40:00");

        /*
         * test
         */
        int passed = TransferRequestSchedule.passedCount(createdAt, now.atZone(SEOUL).toInstant());

        /*
         * verify
         */
        assertThat(passed).isEqualTo(expected);
    }

    @DisplayName("startedBefore - 대상 조회 기준(시작 < 기준)이 차례 시각과 맞는다. 2번째만 차례 시각이 now 와 똑같은 정산을 다음 회차로 미룬다")
    @ParameterizedTest(name = "{0} 번째")
    @ValueSource(ints = {2, 3})
    void startedBefore(int nth) {

        /*
         * settings
         */
        List<Instant> createdAts = grid(seoul("2026-11-01T00:00:00"), Duration.ofMinutes(37), 3 * 24 * 60 / 37);
        createdAts.addAll(List.of(seoul("2026-11-02T00:00:00"), seoul("2026-11-02T09:00:00"), seoul("2026-11-02T23:59:59")));
        List<Instant> nows = grid(seoul("2026-11-02T00:00:00"), Duration.ofMinutes(23), 4 * 24 * 60 / 23);
        nows.addAll(List.of(seoul("2026-11-03T09:59:59"), seoul("2026-11-03T10:00:00"), seoul("2026-11-04T10:00:00"), seoul("2026-11-02T10:00:00")));

        /*
         * test / verify
         */
        for(Instant now : nows) {
            Instant before = TransferRequestSchedule.startedBefore(nth, now);

            for(Instant createdAt : createdAts) {
                Instant due = TransferRequestSchedule.dueOf(createdAt, nth);
                boolean selected = createdAt.isBefore(before);
                boolean isDue = !now.isBefore(due);

                assertThat(!selected || isDue).as("차례 전인 정산을 고르지 않는다 created=%s now=%s", createdAt, now).isTrue();

                if(nth == 2 && due.equals(now)) continue;

                assertThat(selected).as("차례가 지난 정산을 고른다 created=%s now=%s", createdAt, now).isEqualTo(isDue);
            }
        }
    }

    @DisplayName("startedBefore - 3번째는 now 까지 지난 마지막 10시(한국 시각)의 날짜 0시다")
    @ParameterizedTest(name = "now {0} KST → 3번째 {1}")
    @CsvSource({
            "2026-11-05T09:59:59, 2026-11-04T00:00:00",
            "2026-11-05T10:00:00, 2026-11-05T00:00:00",
            "2026-11-05T23:30:00, 2026-11-05T00:00:00"})
    void startedBefore_daily(LocalDateTime now, LocalDateTime third) {

        /*
         * settings
         */
        Instant at = now.atZone(SEOUL).toInstant();

        /*
         * test
         */
        Instant thirdBefore = TransferRequestSchedule.startedBefore(3, at);

        /*
         * verify
         */
        assertThat(thirdBefore).isEqualTo(third.atZone(SEOUL).toInstant());
    }

    @DisplayName("startedBefore - 2·3 번째 밖이면 IllegalArgumentException(1번째는 시작할 때 보낸다)")
    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void startedBefore_outOfRange(int nth) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> TransferRequestSchedule.startedBefore(nth, Instant.EPOCH));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Instant> grid(Instant start, Duration step, int count) {
        List<Instant> instants = new ArrayList<>();
        for(int i = 0; i < count; i++) instants.add(start.plus(step.multipliedBy(i)));

        return instants;
    }

    private static Instant seoul(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SEOUL).toInstant();
    }
}
