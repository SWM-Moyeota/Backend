package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.ReservationReminderNotifier;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationReminderService {
    private final Parties parties;
    private final AfterCommitExecutor afterCommitExecutor;
    private final ReservationReminderNotifier reminderNotifier;

    @Transactional
    public void remind(Long partyId) {
        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        if(party.getReminderSentAt() != null || !party.getStatus().isRecruiting()) return;

        party.markReminderSent(Instant.now());
        parties.save(party);

        List<Long> memberIds = party.getMembers().stream().map(PartyMember::getMemberId).toList();
        Instant departureAt = party.getDepartureAt();

        afterCommitExecutor.execute("party.reminder", () -> reminderNotifier.notifyDepartureSoon(partyId, memberIds, departureAt));

        log.info("예약 방 출발 전 알림 partyId={}, members={}", partyId, memberIds.size());
    }
}
