package team.codingforest.moyeota.matching.party.domain;

import java.util.List;

/**
 *  자동 정리(Party.autoClose) 결과
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
