package team.codingforest.moyeota.matching.record.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RideRecordTest {
    private static final Long 방 = 10L;

    @Test
    void 끝난_동승은_출발지_도착지와_승차_시각을_남긴다() {
        Instant 승차 = Instant.parse("2026-10-09T13:42:11.654321Z");

        RideRecord rideRecord = RideRecord.create(방, "강남역", "판교역", 승차);

        assertThat(rideRecord.getMatchId()).isEqualTo(방);
        assertThat(rideRecord.getDeparturePlace()).isEqualTo("강남역");
        assertThat(rideRecord.getDestinationPlace()).isEqualTo("판교역");
        assertThat(rideRecord.getPickupTime()).isEqualTo(승차);
    }

    @Test
    void 복원한_기록은_값을_그대로_가진다() {
        Instant 승차 = Instant.parse("2026-10-09T13:42:11.654321Z");

        RideRecord rideRecord = RideRecord.load(방, "부산대학교 정문", "서면역 1번 출구", 승차);

        assertThat(rideRecord.getMatchId()).isEqualTo(방);
        assertThat(rideRecord.getDeparturePlace()).isEqualTo("부산대학교 정문");
        assertThat(rideRecord.getDestinationPlace()).isEqualTo("서면역 1번 출구");
        assertThat(rideRecord.getPickupTime()).isEqualTo(승차);
    }
}
