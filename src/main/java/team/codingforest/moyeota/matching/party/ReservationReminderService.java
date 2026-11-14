package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.ReservationReminderNotifier;

import java.util.List;

/**
 *  예약 방 출발 30분 전 알림. 언제 보낼지는 스케줄러가 departure_at 구간으로 고른다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationReminderService {
	private final Parties parties;
	private final ReservationReminderNotifier reminderNotifier;

	public void remind(Long partyId) {
		Party party = parties.findById(partyId)
				.orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

		List<Long> recipientIds = party.noticeRecipients();

		if(recipientIds.isEmpty()) {
			log.info("출발 전 알림을 보내지 않음(닫혔거나 혼자인 방) partyId={}", partyId);
			return;
		}

		reminderNotifier.notifyDepartureSoon(partyId, recipientIds, party.getDepartureAt());
		log.info("예약 방 출발 전 알림 partyId={}, 받는 사람={}명", partyId, recipientIds.size());
	}
}