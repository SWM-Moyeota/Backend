package team.codingforest.moyeota.matching.record.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideRecordTest {
    private static final Long 방 = 10L;
    private static final Long 기사 = 9L;

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
    void 탑승하면_탑승_시각과_운행_정보가_남고_하차_시각과_요금은_비어_있다() {
        Instant 직전 = Instant.now().truncatedTo(ChronoUnit.MICROS);

        RideRecord rideRecord = RideRecord.pickUp(방, 기사, "강남역", "판교역");

        assertThat(rideRecord.getMatchId()).isEqualTo(방);
        assertThat(rideRecord.getTaxiDriverId()).isEqualTo(기사);
        assertThat(rideRecord.getDeparturePlace()).isEqualTo("강남역");
        assertThat(rideRecord.getDestinationPlace()).isEqualTo("판교역");
        assertThat(rideRecord.getPickupTime()).isBetween(직전, Instant.now());
        assertThat(rideRecord.getDropoffTime()).isNull();
        assertThat(rideRecord.getTotalFare()).isNull();
        assertThat(rideRecord.isDroppedOff()).isFalse();
    }

    @Test
    void 하차하면_하차_시각과_요금이_채워지고_탑승_시각은_그대로다() {
        RideRecord rideRecord = RideRecord.pickUp(방, 기사, "강남역", "판교역");
        Instant 탑승시각 = rideRecord.getPickupTime();
        Instant 직전 = Instant.now().truncatedTo(ChronoUnit.MICROS);

        rideRecord.dropOff(15000);

        assertThat(rideRecord.getDropoffTime()).isBetween(직전, Instant.now()).isAfterOrEqualTo(탑승시각);
        assertThat(rideRecord.getTotalFare()).isEqualTo(15000);
        assertThat(rideRecord.getPickupTime()).isEqualTo(탑승시각);
        assertThat(rideRecord.isDroppedOff()).isTrue();
    }

    @Test
    void 탑승_시각과_하차_시각은_마이크로초_단위로_잘린다() {
        RideRecord rideRecord = RideRecord.pickUp(방, 기사, "강남역", "판교역");

        rideRecord.dropOff(15000);

        assertThat(rideRecord.getPickupTime().getNano() % 1_000).isZero();
        assertThat(rideRecord.getDropoffTime().getNano() % 1_000).isZero();
    }

    @Test
    void 이미_하차한_기록은_다시_하차할_수_없고_값도_바뀌지_않는다() {
        RideRecord rideRecord = RideRecord.pickUp(방, 기사, "강남역", "판교역");
        rideRecord.dropOff(15000);
        Instant 하차시각 = rideRecord.getDropoffTime();

        assertThatThrownBy(() -> rideRecord.dropOff(20000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_RIDING);

        assertThat(rideRecord.getDropoffTime()).isEqualTo(하차시각);
        assertThat(rideRecord.getTotalFare()).isEqualTo(15000);
    }

    @Test
    void 탑승_기록_없이_만든_기록은_탑승_시각이_비어_있고_하차할_수_있다() {
        RideRecord rideRecord = RideRecord.withoutPickup(방, 기사, "강남역", "판교역");

        rideRecord.dropOff(15000);

        assertThat(rideRecord.getPickupTime()).isNull();
        assertThat(rideRecord.getDropoffTime()).isNotNull();
        assertThat(rideRecord.getTotalFare()).isEqualTo(15000);
        assertThat(rideRecord.getTaxiDriverId()).isEqualTo(기사);
    }

    @Test
    void 복원한_기록은_값을_그대로_가진다() {
        Instant 탑승 = Instant.parse("2026-10-09T13:05:02.123456Z");
        Instant 하차 = Instant.parse("2026-10-09T13:42:11.654321Z");

        RideRecord rideRecord = RideRecord.load(방, 기사, "부산대학교 정문", "서면역 1번 출구", 탑승, 하차, 9600);

        assertThat(rideRecord.getPickupTime()).isEqualTo(탑승);
        assertThat(rideRecord.getDropoffTime()).isEqualTo(하차);
        assertThat(rideRecord.getTotalFare()).isEqualTo(9600);
        assertThat(rideRecord.isDroppedOff()).isTrue();
    }

    @Test
    void 복원한_하차_완료_기록도_다시_하차할_수_없다() {
        RideRecord rideRecord = RideRecord.load(방, 기사, "강남역", "판교역", null, Instant.parse("2026-10-09T13:42:11Z"), 9600);

        assertThatThrownBy(() -> rideRecord.dropOff(10000)).isInstanceOf(BusinessException.class);
    }
}
