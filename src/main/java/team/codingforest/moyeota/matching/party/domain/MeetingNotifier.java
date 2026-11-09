package team.codingforest.moyeota.matching.party.domain;

import java.util.List;

/**
 *  약속 시각 전후 푸시. 받을 사람과 보낼지 말지(방해 금지 시간 등)는 부르는 쪽이 정하고, 여기서는 보내기만 한다
 */
public interface MeetingNotifier {

    /** 약속 시각 알림 - 약속이 정해진 방의 멤버 전원 */
    void notifyMeetingTime(Long partyId, List<Long> memberIds);

    /** 출발 재촉 - 출발 버튼을 누르는 방장 */
    void remindDeparture(Long partyId, List<Long> memberIds);
}
