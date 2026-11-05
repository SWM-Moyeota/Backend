package team.codingforest.moyeota.matching.record.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class RideRecord {
    private final Long matchId;
    private final String departurePlace;
    private final String destinationPlace;
    private final Instant dropoffTime;

    private RideRecord(Long matchId, String departurePlace, String destinationPlace, Instant dropoffTime) {
        this.matchId = matchId;
        this.departurePlace = departurePlace;
        this.destinationPlace = destinationPlace;
        this.dropoffTime = dropoffTime;
    }

    public static RideRecord withoutDriver(Long matchId, String departurePlace, String destinationPlace) {
        return new RideRecord(matchId, departurePlace, destinationPlace, now());
    }

    public static RideRecord load(Long matchId, String departurePlace, String destinationPlace, Instant dropoffTime) {
        return new RideRecord(matchId, departurePlace, destinationPlace, dropoffTime);
    }

    public boolean isDroppedOff() {
        return dropoffTime != null;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
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

	public Instant getDropoffTime() {
		return dropoffTime;
	}
}
