package team.codingforest.moyeota.matching.domain.enums;

public enum PartyStatus {
    ACTIVE, COMPLETED, MATCHING, DRIVER_ASSIGNED, IN_RIDE, FINISHED, CANCELED;

    public boolean isOngoing() {
        return !isClosed();
    }

    /** 더이상 방 상태를 변경할 수 없음
     */
    public boolean isClosed() {
        return this == CANCELED || this == FINISHED;
    }

    public boolean isRecruiting() {
        return this == ACTIVE || this == COMPLETED;
    }

    public boolean isRiding() {
        return this == IN_RIDE;
    }
}
