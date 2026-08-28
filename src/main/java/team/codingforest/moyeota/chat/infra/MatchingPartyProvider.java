package team.codingforest.moyeota.chat.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.chat.domain.PartyProvider;
import team.codingforest.moyeota.chat.domain.PartySnapshot;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartyChatSummary;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingPartyProvider implements PartyProvider {
    private final PartyAccess partyAccess;

    @Override
    public Optional<PartySnapshot> findSnapshot(Long partyId) {
        try {
            PartyChatSummary summary = partyAccess.findChatSummary(partyId);
            return Optional.of(new PartySnapshot(
                    summary.id(), summary.users(), summary.departure(), summary.destination()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("파티 조회 실패 partyId={} 사유={}", partyId, e.getMessage());
            return Optional.empty();
        }
    }
}
