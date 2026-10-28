package team.codingforest.moyeota.settlement.ledger.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public record SettlementPeriod(Instant start, Instant end) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public SettlementPeriod {
        if(start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("정산 기간은 시작이 끝보다 앞서야 합니다.");
        }
    }

    public static SettlementPeriod weekOf(Instant at) {
        return weekOf(LocalDate.ofInstant(at, ZONE));
    }

    public static SettlementPeriod weekOf(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new SettlementPeriod(startOf(monday), startOf(monday.plusWeeks(1)));
    }

    public SettlementPeriod previous() {
        return weekOf(startDate().minusWeeks(1));
    }

    public boolean contains(Instant at) {
        return !at.isBefore(start) && at.isBefore(end);
    }

    public LocalDate startDate() {
        return LocalDate.ofInstant(start, ZONE);
    }

    public LocalDate lastDate() {
        return LocalDate.ofInstant(end, ZONE).minusDays(1);
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
