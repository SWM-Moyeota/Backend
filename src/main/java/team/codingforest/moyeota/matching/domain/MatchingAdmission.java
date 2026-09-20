package team.codingforest.moyeota.matching.domain;

import java.util.function.Supplier;

/**
 *  입장 직렬화 경계. 작업의 DB 커밋/롤백이 끝나기 전에는 잠금을 해제하지 않는다.
 *  방 생성은 사용자 잠금만, 참가는 사용자 → 방 순서로 두 잠금을 잡는다. 순서를 고정해 교착을 막는다.
 */
public interface MatchingAdmission {
    /** 사용자별 직렬화 - 방 생성처럼 아직 방이 없는 작업 */
    <T> T execute(Long memberId, Supplier<T> operation);

    /** 사용자 → 방 순서 직렬화 - 참가. 방 잠금 대기자는 DB 트랜잭션을 열기 전에 기다린다 */
    <T> T execute(Long memberId, Long partyId, Supplier<T> operation);
}
