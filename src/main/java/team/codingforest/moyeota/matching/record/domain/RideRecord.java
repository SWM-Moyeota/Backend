package team.codingforest.moyeota.matching.record.domain;

import java.time.Instant;

public class RideRecord {
    private final Long matchId;
    private final String departurePlace;
    private final String destinationPlace;
    private final Instant pickupTime;

    private RideRecord(Long matchId, String departurePlace, String destinationPlace, Instant pickupTime) {
        this.matchId = matchId;
        this.departurePlace = departurePlace;
        this.destinationPlace = destinationPlace;
        this.pickupTime = pickupTime;
    }

    public static RideRecord create(Long matchId, String departurePlace, String destinationPlace, Instant pickupTime) {
        return new RideRecord(matchId, departurePlace, destinationPlace, pickupTime);
    }

    public static RideRecord load(Long matchId, String departurePlace, String destinationPlace, Instant pickupTime) {
        return new RideRecord(matchId, departurePlace, destinationPlace, pickupTime);
    }

	public Long getMatchId() {
		return matchId;
	}

	public String getDeparturePlace() {
		return departurePlace;
	}

	public String getDestinationPlace() {
		return destinationPlace;
	}

	public Instant getPickupTime() {
		return pickupTime;
	}
}
