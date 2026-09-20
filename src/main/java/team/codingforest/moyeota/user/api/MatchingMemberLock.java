package team.codingforest.moyeota.user.api;

/** 사용자 모듈 소유 행의 잠금. 호출자의 DB 트랜잭션이 끝날 때까지 유지한다. */
public interface MatchingMemberLock {
    void acquire(Long memberId);
}
