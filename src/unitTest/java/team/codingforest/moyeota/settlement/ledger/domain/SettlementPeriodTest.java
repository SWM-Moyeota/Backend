package team.codingforest.moyeota.settlement.ledger.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementPeriodTest {
    private static final LocalDate 시월_26일_월요일 = LocalDate.of(2026, 10, 26);
    private static final LocalDate 십일월_1일_일요일 = LocalDate.of(2026, 11, 1);

    @Test
    void 일요일_KST_자정_직전은_그_주에_속한다() {
        SettlementPeriod period = SettlementPeriod.weekOf(Instant.parse("2026-11-01T14:59:59.999999Z"));

        assertThat(period.startDate()).isEqualTo(시월_26일_월요일);
        assertThat(period.lastDate()).isEqualTo(십일월_1일_일요일);
        assertThat(period.start()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
    }

    @Test
    void 월요일_KST_자정부터는_다음_주에_속한다() {
        SettlementPeriod period = SettlementPeriod.weekOf(Instant.parse("2026-11-01T15:00:00Z"));

        assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(period.lastDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(period.start()).isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-11-08T15:00:00Z"));
    }

    @Test
    void UTC_로는_일요일이어도_KST_로_월요일이면_다음_주다() {
        SettlementPeriod UTC_일요일_15시_30분 = SettlementPeriod.weekOf(Instant.parse("2026-11-01T15:30:00Z"));
        SettlementPeriod UTC_일요일_14시_59분 = SettlementPeriod.weekOf(Instant.parse("2026-11-01T14:59:00Z"));

        assertThat(UTC_일요일_15시_30분.startDate()).as("UTC 11-01(일) 15:30 은 KST 11-02(월) 00:30 이다").isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(UTC_일요일_14시_59분.startDate()).as("UTC 11-01(일) 14:59 는 KST 11-01(일) 23:59 다").isEqualTo(시월_26일_월요일);
    }

    @Test
    void 수요일_날짜는_그_주_월요일부터_일요일까지다() {
        SettlementPeriod period = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 28));

        assertThat(period.startDate()).isEqualTo(시월_26일_월요일);
        assertThat(period.lastDate()).isEqualTo(십일월_1일_일요일);
        assertThat(period.start()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31", "2026-11-01"})
    void 월요일부터_일요일까지_어느_날짜든_같은_주다(String date) {
        assertThat(SettlementPeriod.weekOf(LocalDate.parse(date))).isEqualTo(SettlementPeriod.weekOf(시월_26일_월요일));
    }

    @Test
    void 일요일_다음_날인_월요일은_다른_주다() {
        assertThat(SettlementPeriod.weekOf(LocalDate.of(2026, 11, 2))).isNotEqualTo(SettlementPeriod.weekOf(십일월_1일_일요일));
        assertThat(SettlementPeriod.weekOf(LocalDate.of(2026, 10, 25))).isNotEqualTo(SettlementPeriod.weekOf(시월_26일_월요일));
    }

    @Test
    void 같은_KST_주의_시각은_모두_날짜로_구한_주와_같은_정산_기간이다() {
        SettlementPeriod 월요일_자정 = SettlementPeriod.weekOf(Instant.parse("2026-10-25T15:00:00Z"));
        SettlementPeriod 수요일_정오 = SettlementPeriod.weekOf(Instant.parse("2026-10-28T03:00:00Z"));
        SettlementPeriod 일요일_밤 = SettlementPeriod.weekOf(Instant.parse("2026-11-01T14:59:59Z"));

        assertThat(월요일_자정).isEqualTo(수요일_정오).isEqualTo(일요일_밤).isEqualTo(SettlementPeriod.weekOf(LocalDate.of(2026, 10, 28)));
    }

    @Test
    void 주_정산_기간은_7일이고_월요일에_시작해_일요일에_끝난다() {
        SettlementPeriod period = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 28));

        assertThat(Duration.between(period.start(), period.end())).isEqualTo(Duration.ofDays(7));
        assertThat(period.startDate().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(period.lastDate().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    void 달을_넘어가는_주는_앞_달_월요일부터_다음_달_일요일까지다() {
        SettlementPeriod period = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 1));

        assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(period.lastDate()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(period.start()).isEqualTo(Instant.parse("2026-09-27T15:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-10-04T15:00:00Z"));
    }

    @Test
    void 해를_넘어가는_주는_12월_월요일부터_1월_일요일까지다() {
        SettlementPeriod 십이월_31일 = SettlementPeriod.weekOf(LocalDate.of(2026, 12, 31));
        SettlementPeriod 일월_1일 = SettlementPeriod.weekOf(Instant.parse("2026-12-31T15:00:00Z"));

        assertThat(십이월_31일).isEqualTo(일월_1일);
        assertThat(십이월_31일.startDate()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(십이월_31일.lastDate()).isEqualTo(LocalDate.of(2027, 1, 3));
        assertThat(십이월_31일.start()).isEqualTo(Instant.parse("2026-12-27T15:00:00Z"));
        assertThat(십이월_31일.end()).isEqualTo(Instant.parse("2027-01-03T15:00:00Z"));
    }

    @Test
    void 윤년_2월_29일이_낀_주도_7일이다() {
        SettlementPeriod period = SettlementPeriod.weekOf(LocalDate.of(2028, 2, 29));

        assertThat(period.startDate()).isEqualTo(LocalDate.of(2028, 2, 28));
        assertThat(period.lastDate()).isEqualTo(LocalDate.of(2028, 3, 5));
        assertThat(Duration.between(period.start(), period.end())).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void 바로_앞_주는_7일_전_월요일부터_이_주_월요일_KST_자정까지다() {
        SettlementPeriod period = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 28));

        SettlementPeriod previous = period.previous();

        assertThat(previous.startDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(previous.lastDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(previous.end()).isEqualTo(period.start());
    }

    @Test
    void 해의_첫_주의_바로_앞_주는_지난해_12월_주다() {
        SettlementPeriod previous = SettlementPeriod.weekOf(LocalDate.of(2027, 1, 5)).previous();

        assertThat(previous).isEqualTo(SettlementPeriod.weekOf(LocalDate.of(2026, 12, 31)));
        assertThat(previous.startDate()).isEqualTo(LocalDate.of(2026, 12, 28));
    }

    @Test
    void 월요일_KST_자정부터_일요일_KST_자정_직전까지가_그_주에_든다() {
        SettlementPeriod period = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 28));

        assertThat(period.contains(Instant.parse("2026-10-25T15:00:00Z"))).isTrue();
        assertThat(period.contains(Instant.parse("2026-10-28T03:00:00Z"))).isTrue();
        assertThat(period.contains(Instant.parse("2026-11-01T14:59:59.999999Z"))).isTrue();
    }

    @Test
    void 월요일_KST_자정_직전과_다음_월요일_KST_자정은_그_주에_들지_않는다() {
        SettlementPeriod period = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 28));

        assertThat(period.contains(Instant.parse("2026-10-25T14:59:59.999999Z"))).isFalse();
        assertThat(period.contains(Instant.parse("2026-11-01T15:00:00Z"))).isFalse();
    }

    @Test
    void 시작이나_끝이_없거나_시작이_끝보다_앞서지_않으면_만들_수_없다() {
        Instant 시각 = Instant.parse("2026-10-28T15:00:00Z");

        assertThatThrownBy(() -> new SettlementPeriod(null, 시각)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SettlementPeriod(시각, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SettlementPeriod(시각, 시각)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SettlementPeriod(시각, 시각.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
