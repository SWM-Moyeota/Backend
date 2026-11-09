package team.codingforest.moyeota.matching.party.domain;

/**
 *  방 상태와 전이. 상태를 바꾸는 코드는 Party 안에만 있다
 *
 *  ACTIVE ──confirm(실시간)──▶ COMPLETED ──depart / autoClose(도착자 있음)──▶ FINISHED
 *    │                            │
 *    ├─ leave(마지막 멤버)          ├─ leave(혼자 남음)
 *    ├─ expireRecruiting(혼자)      └─ autoClose(도착자 없음)
 *    └─ autoClose ────────────────────────────────────────────────────────▶ CANCELED
 *
 *  - ACTIVE    : 모집 중. 정원이 차도 ACTIVE 다(참여는 PARTY_FULL 로 막힌다)
 *  - COMPLETED : 방장이 약속을 확정한 실시간 방. 예약 방은 만들 때부터 약속이 있어 이 상태를 거치지 않는다
 *  - FINISHED  : 동승이 끝났다. 도착한 멤버가 탄 사람이다
 *  - CANCELED  : 동승 없이 닫혔다
 *
 *  match_room.status 는 @Enumerated(STRING) 이라 값 이름을 바꾸면 마이그레이션이 필요하다
 */
public enum PartyStatus {
    ACTIVE, COMPLETED, FINISHED, CANCELED;

    /** 아직 닫히지 않은 방 - 참여·나가기·확정·도착·출발·알림의 대상 */
    public boolean isOpen() {
        return !isClosed();
    }

    public boolean isClosed() {
        return this == FINISHED || this == CANCELED;
    }
}
