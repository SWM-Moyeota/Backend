package team.codingforest.moyeota.matching.noshow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.noshow.domain.NoShowHistory;
import team.codingforest.moyeota.matching.noshow.domain.NoShowObjection;
import team.codingforest.moyeota.matching.noshow.domain.NoShowObjections;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionStatus;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionSummary;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.user.api.NoShowPenalty;

import java.time.Instant;
import java.util.List;

/**
 *  노쇼 이의 신청(멤버)과 처리(운영자). 받아들이면 같은 트랜잭션에서 멤버 행의 노쇼를 무효로 돌리고(no_show_voided_at)
 *  user 모듈의 노쇼도 무효로 돌린다(노쇼 횟수에서 빠지고 지금 걸린 제한 해제)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoShowObjectionService {
    static final int HISTORY_LIMIT = 20;
    static final int OPERATOR_LIST_LIMIT = 100;

    private final Parties parties;
    private final NoShowObjections objections;
    private final NoShowPenalty noShowPenalty;

    /**
     *  이 방에서 노쇼로 기록된(무효로 돌아가지 않은) 멤버만, 방마다 한 번 낼 수 있다
     */
    @Transactional
    public void object(Long partyId, Long memberId, String reason) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        party.ensureNoShow(memberId);

        if(objections.exists(memberId, partyId)) throw new BusinessException(MatchingErrorCode.ALREADY_OBJECTED);

        NoShowObjection saved;
        try {
            saved = objections.save(NoShowObjection.file(memberId, partyId, reason, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            // 위 확인과 저장 사이에 같은 신청이 먼저 들어간 경우 - (user_id, match_id) 유니크 제약이 막는다
            throw new BusinessException(MatchingErrorCode.ALREADY_OBJECTED);
        }

        log.info("노쇼 이의 신청 objectionId={}, partyId={}, memberId={}", saved.getId(), partyId, memberId);
    }

    @Transactional(readOnly = true)
    public List<NoShowHistory> findMyNoShows(Long memberId) {
        return objections.findNoShowHistory(memberId, HISTORY_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ObjectionSummary> findObjections(ObjectionStatus status) {
        return objections.findByStatus(status, OPERATOR_LIST_LIMIT);
    }

    /**
     *  받아들인다 - 노쇼를 무효로 돌리고 제재를 줄인다. 이미 무효였던 노쇼(다른 경로로 먼저 무효가 된 경우)는 제재를 두 번 줄이지 않는다
     */
    @Transactional
    public void accept(Long objectionId) {
        Instant now = Instant.now();
        NoShowObjection objection = getForUpdate(objectionId);

        objection.accept(now);

        Party party = parties.findByIdForUpdate(objection.getPartyId())
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        boolean voided = party.voidNoShow(objection.getUserId(), now);
        parties.save(party);

        if(voided) {
            noShowPenalty.cancel(objection.getUserId(), objection.getPartyId(), now);
        }

        objections.save(objection);

        log.info("노쇼 이의 인정 objectionId={}, partyId={}, memberId={}, voided={}", objectionId, objection.getPartyId(), objection.getUserId(), voided);
    }

    @Transactional
    public void reject(Long objectionId) {
        NoShowObjection objection = getForUpdate(objectionId);

        objection.reject(Instant.now());
        objections.save(objection);

        log.info("노쇼 이의 거절 objectionId={}, partyId={}, memberId={}", objectionId, objection.getPartyId(), objection.getUserId());
    }

    private NoShowObjection getForUpdate(Long objectionId) {
        return objections.findByIdForUpdate(objectionId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.OBJECTION_NOT_FOUND));
    }
}
