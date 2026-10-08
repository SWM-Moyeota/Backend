package team.codingforest.moyeota.payment.infrastructure.portone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 포트원 V2 에러 본문. type 이 에러 종류(PG_PROVIDER, ALREADY_PAID, BILLING_KEY_NOT_FOUND ...) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneErrorResponse(String type, String message, String pgCode, String pgMessage) {

    public String failCode() {
        if("PG_PROVIDER".equals(type) && pgCode != null && !pgCode.isBlank()) return "PG_" + pgCode;
        return type != null ? type : "UNKNOWN";
    }
}
