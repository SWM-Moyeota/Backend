package team.codingforest.moyeota.payment.dto;

/**
 *  결제사에서 조회한 빌링키 상태.
 *  customerId 는 앱이 빌링키 발급 시 넣은 값 - 유저 publicId 와 비교해 계정 연결을 확인한다.
 *  이 비교는 카드 명의자의 본인 인증을 대신하지 않는다.
 *  card 는 빌링키에 묶인 수단이 카드가 아니면(간편결제·휴대폰 등) null.
 */
public record BillingKeyInfo(boolean issued, String customerId, CardDetail card) {

    public boolean isCard() {
        return card != null;
    }
}
