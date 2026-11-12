package team.codingforest.moyeota.faresplit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitRole;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.FareSplitTransfer;
import team.codingforest.moyeota.faresplit.domain.TossDeeplink;
import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;
import team.codingforest.moyeota.user.api.MemberSummary;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record FareSplitDetailResponse(
        @Schema(description = "방 ID") Long partyId,
        @Schema(description = "OPEN(진행 중) / COMPLETED(모든 송금 확인) / EXPIRED(만든 지 1일 지남)") FareSplitStatus status,
        @Schema(description = "실제로 낸 택시비(원)", example = "18700") int totalFare,
        @Schema(description = "택시비를 다 내고 받는 사람") FareSplitPayer payer,
        @Schema(description = "PAYER(받는 사람) / SENDER(보내는 사람)") FareSplitRole myRole,
        @Schema(description = "내 부담 금액(원). SENDER 는 내가 보낼 금액, PAYER 는 내가 부담한 금액", example = "6220") int myAmount,
        @Schema(description = "받을 계좌의 은행") FareSplitBank bank,
        @Schema(description = "받을 계좌번호(숫자만). OPEN 일 때만 있고 닫히면 null", nullable = true, example = "3333012345678") String accountNumber,
        @Schema(description = "토스 송금 딥링크(supertoss://send?amount=&bank=&accountNo=). SENDER 이고 OPEN 일 때만 있다. 비공식 규격이라 열리지 않을 수 있어 계좌·금액 복사 버튼을 함께 둔다", nullable = true) String tossDeeplink,
        @Schema(description = "보내는 사람별 송금(받는 사람 제외), 방 합류 순") List<FareSplitTransferItem> transfers,
        @Schema(description = "정산을 시작한 시각") Instant createdAt,
        @Schema(description = "COMPLETED·EXPIRED 가 된 시각. OPEN 이면 null", nullable = true) Instant closedAt) {

    public record FareSplitPayer(@Schema(description = "공개 ID") UUID publicId,
                                 @Schema(description = "닉네임") String nickname) {
    }

    public record FareSplitBank(@Schema(description = "은행 코드", example = "KAKAO") Bank code,
                                @Schema(description = "은행 이름", example = "카카오뱅크") String name) {
    }

    public record FareSplitTransferItem(@Schema(description = "보내는 사람 공개 ID") UUID publicId,
                                        @Schema(description = "보내는 사람 닉네임") String nickname,
                                        @Schema(description = "이 사람이 보낼 금액(원)", example = "6240") int amount,
                                        @Schema(description = "NOT_SENT(아직 안 보냄) / SENT(보냈다고 누름) / CONFIRMED(받는 사람이 받았다고 확인)") TransferStatus status,
                                        @Schema(description = "보냈다고 누른 시각", nullable = true) Instant sentAt,
                                        @Schema(description = "받는 사람이 받았다고 확인한 시각", nullable = true) Instant confirmedAt,
                                        @Schema(description = "가린 실명(홍*동). 받는 사람(PAYER)이 볼 때만 있다 - 은행 앱 입금자명과 맞춰 보는 데 쓴다. 보내는 사람에게는 늘 null", nullable = true) String maskedRealName) {
    }

    /**
     *  @param joinOrder       방 멤버 userId, 합류 순. transfers 를 이 순서로 싣는다
     *  @param summaries       userId → 닉네임 등. 없는 사람은 publicId·nickname 이 null
     *  @param maskedRealNames userId → 가린 실명. 받는 사람이 볼 때만 채워서 넘긴다
     */
    public static FareSplitDetailResponse of(FareSplit split, Long viewerId, List<Long> joinOrder, Map<Long, MemberSummary> summaries,
                                             Map<Long, String> maskedRealNames) {
        FareSplitRole role = split.roleOf(viewerId);
        MemberSummary payer = summaries.get(split.getPayerId());
        Optional<AccountNumber> account = split.receivingAccount();

        List<FareSplitTransferItem> transfers = inJoinOrder(split.senderTransfers(), joinOrder).stream()
                .map(transfer -> transferItem(transfer, summaries.get(transfer.getMemberId()), maskedRealNames.get(transfer.getMemberId())))
                .toList();

        return new FareSplitDetailResponse(
                split.getPartyId(),
                split.getStatus(),
                split.getTotalFare(),
                new FareSplitPayer(payer == null ? null : payer.publicId(), payer == null ? null : payer.nickname()),
                role,
                split.amountOf(viewerId),
                new FareSplitBank(split.getBank(), split.getBank().displayName()),
                account.map(AccountNumber::value).orElse(null),
                role == FareSplitRole.SENDER ? account.map(a -> TossDeeplink.of(split.amountOf(viewerId), split.getBank(), a)).orElse(null) : null,
                transfers,
                split.getCreatedAt(),
                split.getClosedAt());
    }

    // 방 합류 순(joinOrder)대로 놓는다. 목록에 없는 사람은 맨 뒤에 userId 순으로 둔다 - 합류 순을 모르면(빈 목록) userId 순이 된다
    private static List<FareSplitTransfer> inJoinOrder(List<FareSplitTransfer> transfers, List<Long> joinOrder) {
        return transfers.stream()
                .sorted(Comparator.comparingInt((FareSplitTransfer transfer) -> orderOf(joinOrder, transfer.getMemberId()))
                        .thenComparing(FareSplitTransfer::getMemberId))
                .toList();
    }

    private static int orderOf(List<Long> joinOrder, Long memberId) {
        int index = joinOrder.indexOf(memberId);

        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static FareSplitTransferItem transferItem(FareSplitTransfer transfer, MemberSummary summary, String maskedRealName) {
        return new FareSplitTransferItem(summary == null ? null : summary.publicId(), summary == null ? null : summary.nickname(),
                transfer.getPayment(), transfer.status(), transfer.getSentAt(), transfer.getConfirmedAt(), maskedRealName);
    }
}
