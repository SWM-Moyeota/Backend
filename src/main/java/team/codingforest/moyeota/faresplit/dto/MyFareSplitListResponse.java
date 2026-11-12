package team.codingforest.moyeota.faresplit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.faresplit.domain.OpenTransfer;
import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;
import team.codingforest.moyeota.faresplit.dto.FareSplitDetailResponse.FareSplitPayer;
import team.codingforest.moyeota.matching.api.dto.PartyPlaces;
import team.codingforest.moyeota.user.api.MemberSummary;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MyFareSplitListResponse(@Schema(description = "내가 보내는 사람인 진행 중(OPEN) 정산 중 내 송금이 요청한 상태인 것. 정산 시작 최신순") List<MyFareSplitItem> fareSplits) {

    public record MyFareSplitItem(@Schema(description = "방 ID - 정산 상세(GET /api/v1/matching/rooms/{partyId}/fare-split)로 들어갈 때 쓴다") Long partyId,
                                  @Schema(description = "출발지 이름") String departure,
                                  @Schema(description = "도착지 이름") String destination,
                                  @Schema(description = "택시비를 다 내고 받는 사람") FareSplitPayer payer,
                                  @Schema(description = "내가 보낼 금액(원)", example = "6240") int myAmount,
                                  @Schema(description = "내 송금 상태") TransferStatus status,
                                  @Schema(description = "정산을 시작한 시각") Instant createdAt) {

        static MyFareSplitItem of(OpenTransfer transfer, PartyPlaces places, MemberSummary payer) {
            return new MyFareSplitItem(transfer.partyId(), places == null ? null : places.departure(), places == null ? null : places.destination(),
                    new FareSplitPayer(payer == null ? null : payer.publicId(), payer == null ? null : payer.nickname()),
                    transfer.payment(), transfer.status(), transfer.createdAt());
        }
    }

    /**
     *  @param places partyId → 출발지·도착지 이름. 없는 방은 departure·destination 이 null
     *  @param payers payerId → 닉네임 등. 없는 사람은 publicId·nickname 이 null
     */
    public static MyFareSplitListResponse of(List<OpenTransfer> transfers, Map<Long, PartyPlaces> places, Map<Long, MemberSummary> payers) {
        return new MyFareSplitListResponse(transfers.stream()
                .map(t -> MyFareSplitItem.of(t, places.get(t.partyId()), payers.get(t.payerId())))
                .toList());
    }
}
