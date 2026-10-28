package team.codingforest.moyeota.settlement.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record WeeklySettlementResponse(@Schema(description = "조회한 주의 첫날 - 월요일(KST)", example = "2026-10-26") LocalDate weekStart,
                                       @Schema(description = "조회한 주의 마지막 날 - 일요일(KST). 정산 기간은 weekStart 00:00 ~ weekEnd 다음 날(월요일) 00:00(KST) 이다", example = "2026-11-01") LocalDate weekEnd,
                                       @Schema(description = "이 주 정산서 ID. 상세 조회의 settlementId 로 쓴다. 정산서는 주가 끝난 뒤 월요일 00:05(KST) 배치가 기사마다 만들고, 이때 생성에 실패한 기사는 같은 날 09:05 까지 매시 5분 배치가 다시 만든다. 이번 주, 미래 주, 정산서가 아직 없는 지난 주는 null", nullable = true) Long settlementId,
                                       @Schema(description = "정산 금액(원) - 운행별 settlementAmount(= totalFare + platformPromotion, 수수료 없음)의 합. 운행은 기사가 요금을 입력해 마치면 결제 결과와 상관없이 바로 정산 원장에 기록된다. 이번 주는 이번 주에 끝났고 아직 정산서에 담기지 않은 운행의 합인 예정 금액이고 days 의 amount 합과 같다. 정산서가 있는 주는 정산서 금액이고 정산서를 만든 뒤에는 바뀌지 않는다. 이 금액도 보통 days 의 amount 합과 같지만, 앞선 주 정산서 생성이 월요일 재시도까지 모두 실패해 그 주 운행이 함께 담긴 정산서는 그 운행이 days 에 보이지 않으므로 days 의 amount 합보다 크다. 정산서가 없는 지난 주와 미래 주는 0") int amount,
                                       @Schema(description = "정산 상태. 이번 주, 미래 주, 정산서가 없는 지난 주는 null. CONFIRMED - 정산서가 만들어져 지급 대기 또는 지급 진행 중 / HOLD - 지급 계좌가 없어 지급 보류 / PAID - 지급 완료 / FAILED - 지급 실패. 지급은 매일 10:00(KST)에 하므로 월요일 00:05 에 만든 정산서는 그날 10:00 에 지급하고, 보류는 계좌를 등록한 뒤, 실패는 정해진 횟수 안에서 다음 10:00 에 다시 시도한다", nullable = true) SettlementStatus status,
                                       @Schema(description = "요일별 정산 금액. 월요일부터 일요일까지 항상 7칸이고, 운행을 운행 종료 시각(occurredAt)의 KST 날짜로 묶은 합계다. 정산서가 있는 주는 정산서에 담긴 운행, 이번 주는 이번 주에 끝났고 아직 정산서에 담기지 않은 운행으로 센다. 정산서에 함께 담긴 앞선 주 운행은 7칸에 들어가지 않는다. 운행이 없는 날은 amount·rideCount 가 0 이다") List<DailyEarning> days) {

    public record DailyEarning(@Schema(description = "날짜(KST)", example = "2026-10-28") LocalDate date,
                               @Schema(description = "이 날(KST) 운행 종료한 운행의 settlementAmount 합(원). 정산서가 있는 주는 정산서에 담긴 운행만, 이번 주는 아직 정산서에 담기지 않은 운행만 센다. 없으면 0") int amount,
                               @Schema(description = "이 날(KST) 운행 종료한 운행 수. amount 와 같은 운행을 센다. 없으면 0") int rideCount) {

        static DailyEarning of(LocalDate date, List<SettlementLedger> ledgers) {
            return new DailyEarning(date, sum(ledgers), ledgers.size());
        }
    }

    public static WeeklySettlementResponse settled(Settlement settlement, List<SettlementLedger> ledgers) {
        return summarize(settlement.period(), settlement.getId(), settlement.getAmount(), settlement.getStatus(), ledgers);
    }

    public static WeeklySettlementResponse upcoming(SettlementPeriod week, List<SettlementLedger> unsettled) {
        return summarize(week, null, sum(unsettled), null, unsettled);
    }

    public static WeeklySettlementResponse empty(SettlementPeriod week) {
        return summarize(week, null, 0, null, List.of());
    }

    private static WeeklySettlementResponse summarize(SettlementPeriod week, Long settlementId, int amount, SettlementStatus status,
                                                      List<SettlementLedger> ledgers) {
        List<LocalDate> dates = week.startDate().datesUntil(week.lastDate().plusDays(1)).toList();

        Map<LocalDate, List<SettlementLedger>> byDate = ledgers.stream()
                .collect(Collectors.groupingBy(WeeklySettlementResponse::kstDate));

        List<DailyEarning> days = dates.stream()
                .map(date -> DailyEarning.of(date, byDate.getOrDefault(date, List.of())))
                .toList();

        return new WeeklySettlementResponse(week.startDate(), week.lastDate(), settlementId, amount, status, days);
    }

    private static LocalDate kstDate(SettlementLedger ledger) {
        return LocalDate.ofInstant(ledger.getOccurredAt(), SettlementPeriod.ZONE);
    }

    private static int sum(List<SettlementLedger> ledgers) {
        return Math.toIntExact(ledgers.stream().mapToLong(SettlementLedger::getSettlementAmount).sum());
    }
}
