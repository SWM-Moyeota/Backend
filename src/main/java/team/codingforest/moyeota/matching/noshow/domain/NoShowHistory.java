package team.codingforest.moyeota.matching.noshow.domain;

import java.time.Instant;

/**
 *  내 노쇼 기록 한 줄(읽기 모델). 무효로 돌아간 노쇼도 담는다. JPQL 선택 목록을 그대로 받으므로 필드 순서·타입을 쿼리와 맞춘다
 *
 *  @param partyId         노쇼가 난 방
 *  @param departure       방의 출발지 이름
 *  @param destination     방의 도착지 이름
 *  @param meetingAt       방의 약속 시간(match_room.departure_at) - 이용 제한을 세기 시작한 시각
 *  @param noShowAt        노쇼로 찍힌 시각(출발 또는 자동 정리)
 *  @param noShowVoidedAt  운영자가 무효로 돌린 시각. 아직 노쇼면 null
 *  @param objectionStatus 이 방에 낸 이의의 상태. 내지 않았으면 null
 */
public record NoShowHistory(Long partyId, String departure, String destination, Instant meetingAt,
                            Instant noShowAt, Instant noShowVoidedAt, ObjectionStatus objectionStatus) {

    public boolean isVoided() {
        return noShowVoidedAt != null;
    }
}
