package team.codingforest.moyeota.matching.record.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RideRecordTest {
    private static final Long 방 = 10L;

    @Test
    void 기사_없이_끝난_합승은_하차_시각만_남고_기사_탑승_시각_요금은_비어_있다() {
        Instant 직전 = Instant.now().truncatedTo(ChronoUnit.MICROS);

        RideRecord rideRecord = RideRecord.withoutDriver(방, "강남역", "판교역");

        assertThat(rideRecord.getMatchId()).isEqualTo(방);
        assertThat(rideRecord.getTaxiDriverId()).isNull();
        assertThat(rideRecord.getPickupTime()).isNull();
        assertThat(rideRecord.getDropoffTime()).isBetween(직전, Instant.now());
        assertThat(rideRecord.getTotalFare()).isNull();
        assertThat(rideRecord.isDroppedOff()).isTrue();
    }

    @Test
    void 하차_시각은_마이크로초_단위로_잘린다() {
        RideRecord rideRecord = RideRecord.withoutDriver(방, "강남역", "판교역");

        assertThat(rideRecord.getDropoffTime().getNano() % 1_000).isZero();
    }

    @Test
    void 복원한_기록은_값을_그대로_가진다() {
        Instant 탑승 = Instant.parse("2026-10-09T13:05:02.123456Z");
        Instant 하차 = Instant.parse("2026-10-09T13:42:11.654321Z");

        RideRecord rideRecord = RideRecord.load(방, null, "부산대학교 정문", "서면역 1번 출구", 탑승, 하차, 9600);

        assertThat(rideRecord.getPickupTime()).isEqualTo(탑승);
        assertThat(rideRecord.getDropoffTime()).isEqualTo(하차);
        assertThat(rideRecord.getTotalFare()).isEqualTo(9600);
        assertThat(rideRecord.isDroppedOff()).isTrue();
    }
}
