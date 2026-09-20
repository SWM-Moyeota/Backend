package team.codingforest.moyeota.matching.domain;

import java.util.function.Supplier;

/** 사용자별 입장 직렬화. 작업의 DB 커밋/롤백이 끝나기 전에는 잠금을 해제하지 않는다. */
public interface MatchingAdmission {
    <T> T execute(Long memberId, Supplier<T> operation);
}
