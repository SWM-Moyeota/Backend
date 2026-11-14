package team.codingforest.moyeota.matching.party.dto;

import team.codingforest.moyeota.matching.party.domain.Party;

import java.time.Instant;

/** SSE confirmed - 방장이 약속을 확정했다(status 는 COMPLETED). 출발지는 확정 때 만남 장소로 바뀌었을 수 있어 함께 싣는다 */
public record PartyConfirmed(String status, Instant departureAt, Double departureLat, Double departureLng, String departure, long version) {
    public static PartyConfirmed from(Party party) {
        return new PartyConfirmed(party.getStatus().name(), party.getDepartureAt(),
                party.getDepartureLocation().latitude(), party.getDepartureLocation().longitude(), party.getDeparture(), party.getVersion());
    }
}
