package team.codingforest.moyeota.matching.record.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RideRecordTest {
    private static final Long 방 = 10L;

    @Test
    void 끝난_동승은_출발지_도착지와_하차_시각을_남긴다() {
        Instant 직전 = Instant.now().truncatedTo(ChronoUnit.MICROS);

        RideRecord rideRecord = RideRecord.droppedOffNow(방, "강남역", "판교역");

        assertThat(rideRecord.getMatchId()).isEqualTo(방);
        assertThat(rideRecord.getDeparturePlace()).isEqualTo("강남역");
        assertThat(rideRecord.getDestinationPlace()).isEqualTo("판교역");
        assertThat(rideRecord.getDropoffTime()).isBetween(직전, Instant.now());
        assertThat(rideRecord.isDroppedOff()).isTrue();
    }

    @Test
    void 하차_시각은_마이크로초_단위로_잘린다() {
        RideRecord rideRecord = RideRecord.droppedOffNow(방, "강남역", "판교역");

        assertThat(rideRecord.getDropoffTime().getNano() % 1_000).isZero();
    }

    @Test
    void 복원한_기록은_값을_그대로_가진다() {
        Instant 하차 = Instant.parse("2026-10-09T13:42:11.654321Z");

        RideRecord rideRecord = RideRecord.load(방, "부산대학교 정문", "서면역 1번 출구", 하차);

        assertThat(rideRecord.getMatchId()).isEqualTo(방);
        assertThat(rideRecord.getDeparturePlace()).isEqualTo("부산대학교 정문");
        assertThat(rideRecord.getDestinationPlace()).isEqualTo("서면역 1번 출구");
        assertThat(rideRecord.getDropoffTime()).isEqualTo(하차);
        assertThat(rideRecord.isDroppedOff()).isTrue();
    }
}
