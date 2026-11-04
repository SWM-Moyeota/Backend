package team.codingforest.moyeota.matching.party.domain;

import java.util.List;

/**
 *  자동 정리(Party.autoClose) 결과
 *
 *  @param status          정리 후 상태 - FINISHED 또는 CANCELED
 *  @param arrivedCount    출발지에 도착한 멤버 수
 *  @param noShowMemberIds 이번에 노쇼로 찍힌 memberId (합류 순)
 */
public record AutoCloseResult(PartyStatus status, int arrivedCount, List<Long> noShowMemberIds) {
    public AutoCloseResult {
        noShowMemberIds = List.copyOf(noShowMemberIds);
    }

    /**
     *  탑승 기록을 남길 방인가 - 도착한 멤버가 있어 FINISHED 로 끝난 방. 도착한 멤버가 실제로 탄 사람이다.
     *  약속을 정하지 않은 방은 누가 탔는지 알 수 없어 FINISHED 여도 남기지 않는다(이전 자동 종료와 같다)
     */
    public boolean hasRiders() {
        return status == PartyStatus.FINISHED && arrivedCount > 0;
    }
}
