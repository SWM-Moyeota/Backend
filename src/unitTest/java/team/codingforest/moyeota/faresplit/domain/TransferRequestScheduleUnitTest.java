package team.codingforest.moyeota.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import team.codingforest.moyeota.common.time.QuietHours;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  송금 요청 차례 - 시작부터 4시간마다, 정산이 만료(시작 1일 뒤)되기 전까지
 */
class TransferRequestScheduleUnitTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @DisplayName("passedCount - now 까지 차례가 지난 요청 수. 시작할 때 1번째, 그 뒤 4시간마다 하나씩 늘고 차례 시각과 같으면 지난 것으로 센다")
    @ParameterizedTest(name = "시작 + {0}분 → {1}")
    @CsvSource({
            "-1, 0",
            "0, 1",
            "239, 1",
            "240, 2",
            "479, 2",
            "480, 3",
            "1199, 5",
            "1200, 6",
            "1439, 6",
            "4320, 6"})
    void passedCount(long minutes, int expected) {

        /*
         * settings
         */
        Instant createdAt = seoul("2026-11-03T21:40:00");

        /*
         * test
         */
        int passed = TransferRequestSchedule.passedCount(createdAt, createdAt.plus(Duration.ofMinutes(minutes)));

        /*
         * verify
         */
        assertThat(passed).isEqualTo(expected);
    }

    @DisplayName("LIMIT - 마지막 차례는 만료 전이고, 그다음 차례는 만료 시각이거나 그 뒤라 빠진 차례가 없다")
    @Test
    void limit_matchesExpiry() {

        /*
         * test
         */
        Duration lastTurn = TransferRequestSchedule.INTERVAL.multipliedBy(TransferRequestSchedule.LIMIT - 1L);
        Duration nextTurn = TransferRequestSchedule.INTERVAL.multipliedBy(TransferRequestSchedule.LIMIT);

        /*
         * verify
         */
        assertThat(lastTurn).isLessThan(FareSplit.EXPIRES_AFTER);
        assertThat(nextTurn).isGreaterThanOrEqualTo(FareSplit.EXPIRES_AFTER);
    }

    @DisplayName("하루 중 언제 시작해도 만료 전에 방해 금지 시간이 아닌 재요청 차례가 적어도 2번 있다")
    @Test
    void reminders_outsideQuietHours() {

        /*
         * settings
         */
        Instant midnight = seoul("2026-11-03T00:00:00");

        /*
         * test / verify
         */
        for(int minute = 0; minute < 24 * 60; minute++) {
            Instant createdAt = midnight.plus(Duration.ofMinutes(minute));
            // 1번째는 시작할 때 보내므로 2번째부터 센다
            long awake = IntStream.rangeClosed(2, TransferRequestSchedule.LIMIT)
                    .mapToObj(nth -> createdAt.plus(TransferRequestSchedule.INTERVAL.multipliedBy(nth - 1L)))
                    .filter(due -> !QuietHours.contains(due))
                    .count();

            assertThat(awake).as("created=%s", createdAt).isGreaterThanOrEqualTo(2);
        }
    }

    private static Instant seoul(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SEOUL).toInstant();
    }
}
