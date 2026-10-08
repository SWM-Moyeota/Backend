package team.codingforest.moyeota.payment.domain;

import lombok.Getter;

import java.time.Instant;

/**
 *  애그리거트 루트 - 유저가 등록한 자동결제(빌링키) 수단. 유저당 활성 수단 1개 정책, 교체 시 이전 수단은 soft delete.
 */
@Getter
public class PaymentMethod {
    private final Long id;
    private final Long userId;
    private final String billingKey;
    private final MethodDetail detail;
    private final Instant createdAt;
    private Instant deletedAt;

    private PaymentMethod(Long id, Long userId, String billingKey, MethodDetail detail, Instant createdAt, Instant deletedAt) {
        this.id = id;
        this.userId = userId;
        this.billingKey = billingKey;
        this.detail = detail;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    public static PaymentMethod register(Long userId, String billingKey, MethodDetail detail, Instant now) {
        return new PaymentMethod(null, userId, billingKey, detail, now, null);
    }

    public void delete(Instant now) {
        if(deletedAt == null) deletedAt = now;
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    public PaymentMethodType getType() {
        return detail.type();
    }

    /**
     *  영속 복원용
     */
    public static PaymentMethod restore(Long id, Long userId, String billingKey, MethodDetail detail, Instant createdAt, Instant deletedAt) {
        return new PaymentMethod(id, userId, billingKey, detail, createdAt, deletedAt);
    }
}
