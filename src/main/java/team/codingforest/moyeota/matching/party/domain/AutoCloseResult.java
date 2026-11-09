package team.codingforest.moyeota.matching.party.domain;

import java.util.List;

/**
 *  자동 정리(Party.autoClose) 결과
 *
 *  @param handled  이번에 닫았으면 true. 조회와 잠금 사이에 이미 닫힌 방이면 false 고 아무것도 바뀌지 않았다
 *  @param status   정리 뒤 상태(handled 가 false 면 원래 상태)
 *  @param riderIds 탄 사람 - 도착한 멤버(합류 순). FINISHED 일 때만 비어 있지 않다
 */
public record AutoCloseResult(boolean handled, PartyStatus status, List<Long> riderIds) {
    static AutoCloseResult skipped(PartyStatus status) {
        return new AutoCloseResult(false, status, List.of());
    }

    static AutoCloseResult closed(PartyStatus status, List<Long> riderIds) {
        return new AutoCloseResult(true, status, riderIds);
    }

    /** 탑승 기록을 남길 방인가 */
    public boolean hasRiders() {
        return !riderIds.isEmpty();
    }
}
