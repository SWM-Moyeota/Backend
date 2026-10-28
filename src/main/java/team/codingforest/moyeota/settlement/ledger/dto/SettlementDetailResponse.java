package team.codingforest.moyeota.settlement.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutAccount;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SettlementDetailResponse(@Schema(description = "정산서 ID") Long settlementId,
                                       @Schema(description = "정산 주의 첫날 - 월요일(KST). 정산서는 이 주가 끝난 뒤 월요일 00:05(KST) 배치(빠진 기사는 같은 날 09:05 까지 매시 5분 배치)가 periodEnd 전에 끝났고 아직 정산서에 담기지 않은 운행을 모아 만든다", example = "2026-10-26") LocalDate date,
                                       @Schema(description = "정산 기간 시작 - date(월요일) 00:00(KST). UTC 로 내려간다", example = "2026-10-25T15:00:00Z") Instant periodStart,
                                       @Schema(description = "정산 기간 끝(이 시각은 포함하지 않음) - 다음 월요일 00:00(KST). UTC 로 내려간다", example = "2026-11-01T15:00:00Z") Instant periodEnd,
                                       @Schema(description = "정산 금액(원) - rides 의 settlementAmount 합. 정산서가 만들어진 뒤에는 금액과 rides 가 바뀌지 않는다") int amount,
                                       @Schema(description = "정산 상태. CONFIRMED - 정산서가 만들어져 지급 대기 또는 지급 진행 중(정상 지급은 월요일 10:00 KST) / HOLD - 지급 계좌가 없어 지급 보류(계좌를 등록하면 매일 10:00 지급 때 다시 시도) / PAID - 지급 완료 / FAILED - 지급 실패(정해진 횟수 안에서 매일 10:00 지급 때 다시 시도)") SettlementStatus status,
                                       @Schema(description = "이 정산서에 담긴 운행별 정산 원장. 운행 종료 시각(occurredAt) 오름차순, 같으면 기록 순") List<LedgerItem> rides,
                                       @Schema(description = "가장 최근 지급 시도 1건. 아직 지급을 시도한 적이 없으면 null") PayoutItem payout) {

    public record LedgerItem(@Schema(description = "운행(방) ID - 탑승 기록의 partyId 와 같다") Long partyId,
                             @Schema(description = "운행 요금(원) - 기사가 운행을 마칠 때 입력한 미터기 요금") int totalFare,
                             @Schema(description = "플랫폼 프로모션 금액(원) - 모여타가 운행 요금에 더해 기사에게 주는 금액. 없으면 0") int platformPromotion,
                             @Schema(description = "이 운행으로 기사가 받을 금액(원) = totalFare + platformPromotion. 수수료는 떼지 않는다") int settlementAmount,
                             @Schema(description = "운행 종료 시각 - 기사가 요금을 입력해 운행을 마친 시각. 이 순간 결제 결과와 상관없이 정산 원장에 기록된다") Instant occurredAt) {

        static LedgerItem of(SettlementLedger ledger) {
            return new LedgerItem(ledger.getMatchId(), ledger.getTotalFare(), ledger.getPlatformPromotion(),
                    ledger.getSettlementAmount(), ledger.getOccurredAt());
        }
    }

    public record PayoutItem(@Schema(description = "지급 상태. REQUESTED - 결제사에 지급 요청 중 / UNKNOWN - 결제사 처리 결과 확인 중(곧 SUCCEED 나 FAILED 로 확정) / SUCCEED - 입금 완료 / FAILED - 지급 실패") PayoutStatus status,
                             @Schema(description = "지급 금액(원) - 지급을 요청한 시점의 정산 금액") int amount,
                             @Schema(description = "입금 은행 - 지급을 요청한 시점의 기사 지급 계좌 은행") BankCode bankCode,
                             @Schema(description = "입금 계좌번호 - 끝 4자리만 보이고 앞자리는 * 로 가린다. 4자리 이하면 전부 *. 지급을 요청한 시점의 계좌", example = "********6789") String maskedAccountNumber,
                             @Schema(description = "지급 실패 사유. status 가 FAILED 일 때만 있고 그 외에는 null") String failReason,
                             @Schema(description = "지급 요청 시각") Instant requestedAt) {

        static PayoutItem of(Payout payout) {
            String failReason = payout.getStatus() == PayoutStatus.FAILED ? payout.getFailReason() : null;

            return new PayoutItem(payout.getStatus(), payout.getAmount(), payout.getBankCode(),
                    PayoutAccount.mask(payout.getAccountNumber()), failReason, payout.getCreatedAt());
        }
    }

    public static SettlementDetailResponse of(Settlement settlement, List<SettlementLedger> ledgers, Payout latestPayout) {
        List<LedgerItem> rides = ledgers.stream().map(LedgerItem::of).toList();
        PayoutItem payout = latestPayout == null ? null : PayoutItem.of(latestPayout);

        return new SettlementDetailResponse(settlement.getId(), settlement.period().startDate(), settlement.getPeriodStart(),
                settlement.getPeriodEnd(), settlement.getAmount(), settlement.getStatus(), rides, payout);
    }
}
