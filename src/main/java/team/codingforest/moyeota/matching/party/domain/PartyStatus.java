package team.codingforest.moyeota.matching.party.domain;

/**
 *  택시 시절 상태 3개(MATCHING·DRIVER_ASSIGNED·IN_RIDE)는 지웠다. match_room.status 는 @Enumerated(STRING) 로 읽으므로
 *  그 값이 남은 행은 읽는 순간 실패한다 - 배포 전에 0건이어야 한다. DB 의 status CHECK 는 컬럼 DROP 과 같은 마이그레이션에서 바꾼다
 */
public enum PartyStatus {
    ACTIVE, COMPLETED, FINISHED, CANCELED;

    public boolean isOngoing() {
        return !isClosed();
    }

	/** 방 상태 변경 불가 **/
    public boolean isClosed() {
        return this == CANCELED || this == FINISHED;
    }

    public boolean isRecruiting() {
        return this == ACTIVE || this == COMPLETED;
    }
}
