package team.codingforest.moyeota.settlement.ledger.domain;

public sealed interface PayoutResult {

    /**
     * 결제사가 지급을 접수함. 입금 결과는 나중에 웹훅이나 조회로 받음
     *
     * @param pgId 결제사 지급 id
     */
    record Accepted(String pgId) implements PayoutResult {
    }

    record Succeeded(String pgId) implements PayoutResult {
    }

    record Failed(String reason) implements PayoutResult {
    }

    record Unknown(String reason) implements PayoutResult {
    }
}
