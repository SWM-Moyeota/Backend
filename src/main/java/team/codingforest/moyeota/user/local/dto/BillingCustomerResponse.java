package team.codingforest.moyeota.user.local.dto;

import java.util.UUID;

/** 인증된 본인의 카드 등록용 정보. 기존 공개 표시용 프로필 응답과 분리한다. */
public record BillingCustomerResponse(UUID uuid, String name, String fullName,
                                      String phoneNumber, String email) {
    @Override
    public String toString() {
        return "BillingCustomerResponse[redacted]";
    }
}
