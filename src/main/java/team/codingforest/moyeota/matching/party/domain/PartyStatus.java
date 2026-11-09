package team.codingforest.moyeota.matching.party.domain;

public enum PartyStatus {
    ACTIVE, COMPLETED, FINISHED, CANCELED;

    /** 아직 닫히지 않은 방 */
    public boolean isOpen() {
        return !isClosed();
    }

    public boolean isClosed() {
        return this == FINISHED || this == CANCELED;
    }
}
