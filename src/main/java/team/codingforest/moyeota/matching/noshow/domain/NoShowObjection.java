package team.codingforest.moyeota.matching.noshow.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Instant;

/**
 *  노쇼 이의 신청 - 노쇼로 기록된 멤버가 방마다 한 번 낸다. 운영자가 받아들이거나(ACCEPTED) 거절한다(REJECTED).
 *  받아들였을 때 노쇼를 무효로 돌리고 제재를 줄이는 일은 서비스가 방(Party)과 user 모듈에 시킨다 - 여기는 신청 자체의 상태만 가진다
 */
public class NoShowObjection {
    public static final int MAX_REASON_LENGTH = 500;

    private final Long id;
    private final Long userId;
    private final Long partyId;
    private final String reason;
    private ObjectionStatus status;
    private final Instant createdAt;
    private Instant resolvedAt;

    private NoShowObjection(Long id, Long userId, Long partyId, String reason, ObjectionStatus status, Instant createdAt, Instant resolvedAt) {
        this.id = id;
        this.userId = userId;
        this.partyId = partyId;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }

    /**
     *  이의 신청. 사유는 앞뒤 공백을 떼고 1~500자. 이 방의 노쇼인지·이미 냈는지는 서비스가 먼저 본다
     */
    public static NoShowObjection file(Long userId, Long partyId, String reason, Instant now) {
        String trimmed = reason == null ? "" : reason.strip();

        if(trimmed.isEmpty() || trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("이의 사유는 1~" + MAX_REASON_LENGTH + "자여야 합니다.");
        }

        return new NoShowObjection(null, userId, partyId, trimmed, ObjectionStatus.PENDING, now, null);
    }

    public static NoShowObjection load(Long id, Long userId, Long partyId, String reason, ObjectionStatus status, Instant createdAt, Instant resolvedAt) {
        return new NoShowObjection(id, userId, partyId, reason, status, createdAt, resolvedAt);
    }

    /** 운영자가 받아들인다. PENDING 이 아니면 OBJECTION_ALREADY_RESOLVED */
    public void accept(Instant now) {
        resolve(ObjectionStatus.ACCEPTED, now);
    }

    /** 운영자가 거절한다. PENDING 이 아니면 OBJECTION_ALREADY_RESOLVED */
    public void reject(Instant now) {
        resolve(ObjectionStatus.REJECTED, now);
    }

    private void resolve(ObjectionStatus result, Instant now) {
        if(status != ObjectionStatus.PENDING) throw new BusinessException(MatchingErrorCode.OBJECTION_ALREADY_RESOLVED);

        status = result;
        resolvedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPartyId() {
        return partyId;
    }

    public String getReason() {
        return reason;
    }

    public ObjectionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
