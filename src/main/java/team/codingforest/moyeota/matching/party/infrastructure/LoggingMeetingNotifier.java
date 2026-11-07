package team.codingforest.moyeota.matching.party.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.party.domain.MeetingNotifier;

import java.util.List;

/** FCM 설정이 없는 환경(로컬·테스트)의 대체 구현 - 보내는 대신 로그만 남긴다 */
@Slf4j
@Component
public class LoggingMeetingNotifier implements MeetingNotifier {

    @Override
    public void notifyMeetingTime(Long partyId, List<Long> memberIds) {
        log.info("[약속 시각 알림] partyId={}, 받는 사람={}명", partyId, memberIds.size());
    }

    @Override
    public void remindDeparture(Long partyId, List<Long> memberIds) {
        log.info("[출발 재촉 알림] partyId={}, 받는 사람={}명", partyId, memberIds.size());
    }

    @Override
    public void notifyNoShow(Long partyId, List<Long> memberIds) {
        log.info("[노쇼 알림] partyId={}, 받는 사람={}명", partyId, memberIds.size());
    }
}
