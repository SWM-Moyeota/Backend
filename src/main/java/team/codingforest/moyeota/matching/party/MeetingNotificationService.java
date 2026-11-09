package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.time.QuietHours;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.MeetingNotifier;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;

import java.time.Instant;
import java.util.List;

/**
 *  약속 시각 알림·출발 재촉. 언제 보낼지는 스케줄러가 departure_at 구간으로 고르고, 누구에게 보낼지는 Party 가 정한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingNotificationService {
	private final Parties parties;
	private final MeetingNotifier meetingNotifier;

	public void notifyMeetingTime(Long partyId) {
		List<Long> recipientIds = find(partyId).noticeRecipients();

		if(recipientIds.isEmpty()) {
			log.info("약속 시각 알림을 보내지 않음(닫혔거나 혼자인 방) partyId={}", partyId);
			return;
		}

		meetingNotifier.notifyMeetingTime(partyId, recipientIds);
		log.info("약속 시각 알림 partyId={}, 받는 사람={}명", partyId, recipientIds.size());
	}

	public void remindDeparture(Long partyId, Instant now) {
		List<Long> recipientIds = find(partyId).departureReminderRecipients();

		if(recipientIds.isEmpty()) {
			log.info("출발 재촉을 보내지 않음(닫혔거나 혼자인 방) partyId={}", partyId);
			return;
		}

		if(QuietHours.contains(now)) {
			log.info("방해 금지 시간이라 출발 재촉을 보내지 않음 partyId={}", partyId);
			return;
		}

		meetingNotifier.remindDeparture(partyId, recipientIds);
		log.info("출발 재촉 partyId={}", partyId);
	}

	private Party find(Long partyId) {
		return parties.findById(partyId)
				.orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));
	}
}