package team.codingforest.moyeota.matching.domain.enums;

public enum PartyStatus {
    ACTIVE, COMPLETED, MATCHING,FINISHED, CANCELED;

    public boolean isOngoing() {
        return this == ACTIVE || this == COMPLETED || this == MATCHING;
    }

    /** 더이상 방 상태를 변경할 수 없음
     */
    public boolean isClosed() {
        return this == CANCELED || this == FINISHED;
    }
}
