package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.common.time.QuietHours;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.MeetingNotifier;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyNotice;

import java.time.Instant;
import java.util.List;

/**
 *  약속 시각 전후 알림 - 약속 시각 알림, 출발 재촉.
 *  언제 차례인지(약속 시각·재촉 간격·30분 지연)는 Party 가, 방해 금지 시간은 QuietHours 가 정하고 여기서는 잠금·저장·커밋 후 전송을 묶는다.
 *  지금 시각(now)은 스케줄러가 한 번 돌 때의 시각으로 넘긴다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingNotificationService {
    private final Parties parties;
    private final AfterCommitExecutor afterCommitExecutor;
    private final MeetingNotifier meetingNotifier;

    /**
     *  약속 시각 알림. 조회와 잠금 사이에 닫히거나 다른 서버가 먼저 처리한 방은 아무것도 하지 않는다
     */
    @Transactional
    public void notifyMeetingTime(Long partyId, Instant now) {
        Party party = lock(partyId);

        PartyNotice notice = party.markMeetingNotified(now);

        if(!notice.handled()) {
            log.info("약속 시각 알림 차례가 아니어서 건너 뜀 partyId={}, status={}, meetingNotifiedAt={}", partyId, party.getStatus(), party.getMeetingNotifiedAt());
            return;
        }

        parties.save(party);

        if(!notice.shouldSend()) {
            log.info("약속 시각 알림을 보내지 않고 처리만 함(30분 넘게 늦었거나 함께 탈 사람이 없음) partyId={}, departureAt={}", partyId, party.getDepartureAt());
            return;
        }

        List<Long> recipientIds = notice.recipientIds();
        afterCommitExecutor.execute("party.meeting-notice", () -> meetingNotifier.notifyMeetingTime(partyId, recipientIds));

        log.info("약속 시각 알림 partyId={}, departureAt={}, 받는 사람={}명", partyId, party.getDepartureAt(), recipientIds.size());
    }

    /**
     *  출발 재촉. 차례가 되면 보내지 않는 경우(혼자인 방, 방해 금지 시간)에도 차례를 넘긴다 - 나중에 몰아 보내지 않는다
     */
    @Transactional
    public void remindDeparture(Long partyId, Instant now) {
        Party party = lock(partyId);

        PartyNotice notice = party.advanceDepartureReminder(now);

        if(!notice.handled()) {
            log.info("출발 재촉 차례가 아니어서 건너 뜀 partyId={}, status={}, count={}", partyId, party.getStatus(), party.getDepartureReminderCount());
            return;
        }

        parties.save(party);

        if(!notice.shouldSend()) {
            log.info("출발 재촉을 보내지 않고 차례만 넘김(혼자인 방) partyId={}, count={}", partyId, party.getDepartureReminderCount());
            return;
        }

        if(QuietHours.contains(now)) {
            log.info("방해 금지 시간이라 출발 재촉을 보내지 않고 차례만 넘김 partyId={}, count={}", partyId, party.getDepartureReminderCount());
            return;
        }

        List<Long> recipientIds = notice.recipientIds();
        afterCommitExecutor.execute("party.departure-reminder", () -> meetingNotifier.remindDeparture(partyId, recipientIds));

        log.info("출발 재촉 partyId={}, count={}, 받는 사람={}명", partyId, party.getDepartureReminderCount(), recipientIds.size());
    }

    private Party lock(Long partyId) {
        return parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));
    }
}
