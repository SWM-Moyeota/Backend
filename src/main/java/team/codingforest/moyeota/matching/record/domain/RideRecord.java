package team.codingforest.moyeota.matching.record.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class RideRecord {
    private final Long matchId;
    private final Long taxiDriverId;
    private final String departurePlace;
    private final String destinationPlace;
    private final Instant pickupTime;
    private final Instant dropoffTime;
    private final Integer totalFare;

    private RideRecord(Long matchId, Long taxiDriverId, String departurePlace, String destinationPlace,
                         Instant pickupTime, Instant dropoffTime, Integer totalFare) {
        this.matchId = matchId;
        this.taxiDriverId = taxiDriverId;
        this.departurePlace = departurePlace;
        this.destinationPlace = destinationPlace;
        this.pickupTime = pickupTime;
        this.dropoffTime = dropoffTime;
        this.totalFare = totalFare;
    }

    public static RideRecord withoutDriver(Long matchId, String departurePlace, String destinationPlace) {
        return new RideRecord(matchId, null, departurePlace, destinationPlace, null, now(), null);
    }

    public static RideRecord load(Long matchId, Long taxiDriverId, String departurePlace, String destinationPlace,
                                    Instant pickupTime, Instant dropoffTime, Integer totalFare) {
        return new RideRecord(matchId, taxiDriverId, departurePlace, destinationPlace, pickupTime, dropoffTime, totalFare);
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

	public Long getTaxiDriverId() {
		return taxiDriverId;
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

	public Instant getDropoffTime() {
		return dropoffTime;
	}

	public Integer getTotalFare() {
		return totalFare;
	}
}
