package team.codingforest.moyeota.matching.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.sse.infrastructure.PartySseRegistry;

@Service
@RequiredArgsConstructor
public class PartySseService {
    private final Parties parties;
    private final PartySseRegistry partySseRegistry;

    /** 방 멤버만 그 방의 변화를 구독할 수 있다 */
    @Transactional(readOnly = true)
    public SseEmitter subscribe(Long partyId, Long memberId) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));
        if(!party.hasMember(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);
        return partySseRegistry.subscribe(partyId, memberId);
    }
}
