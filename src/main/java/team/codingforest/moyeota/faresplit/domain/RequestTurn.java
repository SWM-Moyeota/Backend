package team.codingforest.moyeota.faresplit.domain;

import java.time.Instant;

/**
 *  송금 재요청 대상 후보(읽기 모델) - 받을 계좌가 있는 OPEN 정산의 확인 안 된 송금 하나. JPQL 선택 목록을 그대로 받으므로 필드 순서·타입을 쿼리와 맞춘다
 *
 *  @param partyId      방
 *  @param createdAt    정산 시작 시각
 *  @param requestCount 처리한 송금 요청 차례 수
 */
public record RequestTurn(Long partyId, Instant createdAt, int requestCount) {

    /** now 까지 지난 차례 중 아직 넘기지 않은 것이 있다 - FareSplit.remindTransfers 와 같은 기준이다 */
    public boolean isDue(Instant now) {
        return requestCount < TransferRequestSchedule.passedCount(createdAt, now);
    }
}
