package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class QuietHoursUnitTest {

    @DisplayName("contains - 한국 시각 23:00 부터 08:00 전까지가 방해 금지 시간이다(서버 시간대와 상관없다)")
    @ParameterizedTest(name = "{0} KST → {1}")
    @CsvSource({
            "2026-11-03T22:59:59, false",
            "2026-11-03T23:00:00, true",
            "2026-11-04T00:00:00, true",
            "2026-11-04T03:30:00, true",
            "2026-11-04T07:59:59, true",
            "2026-11-04T08:00:00, false",
            "2026-11-04T12:00:00, false",
            "2026-11-04T22:00:00, false"})
    void contains(LocalDateTime seoulTime, boolean expected) {

        /*
         * settings
         */
        Instant now = seoulTime.atZone(ZoneId.of("Asia/Seoul")).toInstant();

        /*
         * test
         */
        boolean quiet = QuietHours.contains(now);

        /*
         * verify
         */
        assertThat(quiet).isEqualTo(expected);
    }
}
