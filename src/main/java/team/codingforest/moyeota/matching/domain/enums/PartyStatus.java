package team.codingforest.moyeota.matching.domain.enums;

public enum PartyStatus {
    CANCELED, ACTIVE, COMPLETED, MATCHING, FINISHED;

    public boolean isOngoing() {
        return this == ACTIVE || this == COMPLETED || this == MATCHING;
    }
}
