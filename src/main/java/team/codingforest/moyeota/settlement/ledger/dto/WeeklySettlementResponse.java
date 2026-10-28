package team.codingforest.moyeota.settlement.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;

import java.time.LocalDate;
import java.util.List;

public record WeeklySettlementResponse(@Schema(description = "조회한 주의 첫날 - 월요일(KST)", example = "2026-10-26") LocalDate weekStart,
                                       @Schema(description = "조회한 주의 마지막 날 - 일요일(KST). 정산 기간은 weekStart 00:00 ~ weekEnd 다음 날(월요일) 00:00(KST)", example = "2026-11-01") LocalDate weekEnd,
                                       @Schema(description = "이 주 정산서 ID - 정산서 상세 조회에 쓴다. 정산서가 없는 주는 null", nullable = true) Long settlementId,
                                       @Schema(description = "정산 금액(원) - 운행별 settlementAmount(= totalFare + platformPromotion, 수수료 없음)의 합. 정산서가 있는 주는 정산서 금액, 없는 주는 그 주에 끝난 미정산 운행의 예정 금액이고, 앞선 주 운행이 함께 담긴 드문 정산서는 days 의 amount 합보다 클 수 있다") int amount,
                                       @Schema(description = "정산 상태. 정산서가 없는 주는 null. CONFIRMED - 지급 대기 또는 지급 중 / HOLD - 지급 계좌가 없어 지급 보류 / PAID - 지급 완료 / FAILED - 지급 실패", nullable = true) SettlementStatus status,
                                       @Schema(description = "요일별 정산 금액 - 월요일부터 일요일까지 항상 7칸. amount 와 같은 운행을 운행 종료 시각(occurredAt)의 KST 날짜로 묶되, 정산서에 함께 담긴 앞선 주 운행은 넣지 않는다") List<DailyEarning> days) {

    public record DailyEarning(@Schema(description = "날짜(KST)", example = "2026-10-28") LocalDate date,
                               @Schema(description = "이 날(KST) 끝난 운행의 settlementAmount 합(원). 없으면 0") int amount,
                               @Schema(description = "이 날(KST) 끝난 운행 수. 없으면 0") int rideCount) {

        static DailyEarning of(LocalDate date, List<SettlementLedger> ledgers) {
            List<SettlementLedger> sameDay = ledgers.stream()
                    .filter(ledger -> ledger.occurredDate().equals(date))
                    .toList();
            return new DailyEarning(date, sum(sameDay), sameDay.size());
        }
    }

    public static WeeklySettlementResponse settled(Settlement settlement, List<SettlementLedger> ledgers) {
        SettlementPeriod week = settlement.period();
        return new WeeklySettlementResponse(week.startDate(), week.lastDate(), settlement.getId(), settlement.getAmount(),
                settlement.getStatus(), days(week, ledgers));
    }

    public static WeeklySettlementResponse upcoming(SettlementPeriod week, List<SettlementLedger> unsettled) {
        return new WeeklySettlementResponse(week.startDate(), week.lastDate(), null, sum(unsettled), null, days(week, unsettled));
    }

    private static List<DailyEarning> days(SettlementPeriod week, List<SettlementLedger> ledgers) {
        return week.dates().stream()
                .map(date -> DailyEarning.of(date, ledgers))
                .toList();
    }

    private static int sum(List<SettlementLedger> ledgers) {
        return Math.toIntExact(ledgers.stream().mapToLong(SettlementLedger::getSettlementAmount).sum());
    }
}
