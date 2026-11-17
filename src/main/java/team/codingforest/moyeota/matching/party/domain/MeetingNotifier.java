package team.codingforest.moyeota.matching.party.domain;

import java.util.List;

public interface MeetingNotifier {

    /** 약속 시각 알림 - 약속이 정해진 방의 멤버 전원 */
    void notifyMeetingTime(Long partyId, List<Long> recipientIds);

    /** 출발 재촉 - 출발 버튼을 누르는 방장 id가 리스트로 들어온다. */
    void remindDeparture(Long partyId, List<Long> recipientIds);

    /** 방 취소 - 약속을 확정한 방에서 다른 사람이 나가 방이 닫혔을 때 남은 멤버 */
    void notifyCanceled(Long partyId, List<Long> recipientIds);
}
