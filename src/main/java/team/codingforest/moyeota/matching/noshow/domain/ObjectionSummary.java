package team.codingforest.moyeota.matching.noshow.domain;

import java.time.Instant;

/**
 *  운영자 이의 목록 한 줄(읽기 모델). 판단에 필요한 방 정보(장소·약속 시간)를 함께 싣는다.
 *  JPQL 선택 목록을 그대로 받으므로 필드 순서·타입을 쿼리와 맞춘다
 */
public record ObjectionSummary(Long id, Long userId, Long partyId, String departure, String destination, Instant meetingAt,
                               String reason, ObjectionStatus status, Instant createdAt, Instant resolvedAt) {
}
