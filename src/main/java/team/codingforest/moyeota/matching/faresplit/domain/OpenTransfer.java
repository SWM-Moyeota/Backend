package team.codingforest.moyeota.matching.faresplit.domain;

import java.time.Instant;

/**
 *  내가 보내는 사람인 진행 중(OPEN) 정산 한 줄(읽기 모델) - 홈 배너용. JPQL 선택 목록을 그대로 받으므로 필드 순서·타입을 쿼리와 맞춘다.
 *  방 이름은 matching 창구(PartyAccess)로 따로 읽는다
 *
 *  @param partyId         방
 *  @param payerId         받는 사람
 *  @param amountPerPerson 내가 보낼 금액
 *  @param sentAt          내가 보냈다고 누른 시각
 *  @param confirmedAt     받는 사람이 받았다고 확인한 시각
 *  @param createdAt       정산을 시작한 시각
 */
public record OpenTransfer(Long partyId, Long payerId, int amountPerPerson, Instant sentAt, Instant confirmedAt, Instant createdAt) {

    public TransferStatus status() {
        return TransferStatus.of(sentAt, confirmedAt);
    }
}
