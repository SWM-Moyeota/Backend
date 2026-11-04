package team.codingforest.moyeota.settlement.account.domain;

/**
 * 결제사에 등록된 셀러
 *
 * @param sellerId 결제사가 발급한 셀러 id
 * @param status   셀러 상태
 */
public record Seller(String sellerId, SellerStatus status) {
}
