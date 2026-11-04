package team.codingforest.moyeota.matching.noshow.domain;

import java.util.List;
import java.util.Optional;

public interface NoShowObjections {
    /** 새 신청은 바로 INSERT 한다 - (user_id, match_id) 가 겹치면 DataIntegrityViolationException */
    NoShowObjection save(NoShowObjection objection);

    /** 운영자 처리 전 행 잠금 - 두 운영자가 동시에 받아들여도 한 번만 처리된다 */
    Optional<NoShowObjection> findByIdForUpdate(Long id);

    boolean exists(Long userId, Long partyId);

    /** 상태별 목록, 신청 시각 오래된 순 */
    List<ObjectionSummary> findByStatus(ObjectionStatus status, int limit);

    /** 내 노쇼 기록(무효로 돌아간 것 포함), 노쇼로 찍힌 시각 최신순 */
    List<NoShowHistory> findNoShowHistory(Long userId, int limit);
}
