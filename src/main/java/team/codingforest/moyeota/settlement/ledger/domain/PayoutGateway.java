package team.codingforest.moyeota.settlement.ledger.domain;

public interface PayoutGateway {

    /**
     * 지급을 요청함. 같은 지급 키로 다시 보내면 결제사가 처음 결과를 돌려줌
     *
     * @param order 지급 요청
     * @return 접수, 성공, 실패, 모름 중 하나
     */
    PayoutResult request(PayoutOrder order);

    /**
     * 결제사가 접수한 지급의 지금 결과를 조회함
     *
     * @param pgId 결제사 지급 id
     * @return 아직 처리 중이면 Accepted
     */
    PayoutResult find(String pgId);
}
