package team.codingforest.moyeota.matching.party.dto;

import java.time.Instant;

/** SSE confirmed - 방장이 약속 시간을 확정했다. 모집도 끝나 status 는 COMPLETED(기사 호출 모드면 MATCHING) */
public record PartyConfirmed(String status, Instant departureAt, long version) {
    public static PartyConfirmed from(String status, Instant departureAt, long version) {
        return new PartyConfirmed(status, departureAt, version);
    }
}
