package team.codingforest.moyeota.settlement.account.domain;

import lombok.Getter;

/**
 * 결제사가 셀러 요청을 처리하지 못함. 결제사 에러 코드는 어댑터가 Reason으로 바꿔서 넘김
 */
@Getter
public class SellerGatewayException extends RuntimeException {
    private final Reason reason;
    private final String code;

    public SellerGatewayException(Reason reason, String code, Throwable cause) {
        super("셀러 요청 실패 reason=" + reason + ", code=" + code, cause);
        this.reason = reason;
        this.code = code;
    }

    public enum Reason {
        /** 요청 내용이 거절됨. 같은 내용으로 다시 보내도 결과가 같음 */
        REJECTED,
        /** 셀러를 지금은 바꿀 수 없음 */
        LOCKED,
        /** 결제사 장애나 시간 초과, 우리 설정 문제로 결과를 모름 */
        UNAVAILABLE
    }
}
