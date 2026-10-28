package team.codingforest.moyeota.matching.record.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.matching.record.domain.RideRecord;

import java.time.Instant;

@Entity
@Getter
@Table(name = "ride_record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RideRecordEntity extends BaseTimeEntity {

    @Id
    @Column(name = "match_id")
    private Long matchId;

    @Column(name = "taxi_driver_id")
    private Long taxiDriverId;

    @Column(name = "departure_place", nullable = false)
    private String departurePlace;

    @Column(name = "destination_place", nullable = false)
    private String destinationPlace;

    @Column(name = "pickup_time")
    private Instant pickupTime;

    @Column(name = "dropoff_time")
    private Instant dropoffTime;

    @Column(name = "total_fare")
    private Integer totalFare;

    private RideRecordEntity(Long matchId, Long taxiDriverId, String departurePlace, String destinationPlace,
                               Instant pickupTime, Instant dropoffTime, Integer totalFare) {
        this.matchId = matchId;
        this.taxiDriverId = taxiDriverId;
        this.departurePlace = departurePlace;
        this.destinationPlace = destinationPlace;
        this.pickupTime = pickupTime;
        this.dropoffTime = dropoffTime;
        this.totalFare = totalFare;
    }

    public static RideRecordEntity from(RideRecord rideRecord) {
        return new RideRecordEntity(rideRecord.getMatchId(), rideRecord.getTaxiDriverId(), rideRecord.getDeparturePlace(),
                rideRecord.getDestinationPlace(), rideRecord.getPickupTime(), rideRecord.getDropoffTime(), rideRecord.getTotalFare());
    }

    public RideRecordEntity update(RideRecord rideRecord) {
        this.pickupTime = rideRecord.getPickupTime();
        this.dropoffTime = rideRecord.getDropoffTime();
        this.totalFare = rideRecord.getTotalFare();
        return this;
    }

    public RideRecord toDomain() {
        return RideRecord.load(matchId, taxiDriverId, departurePlace, destinationPlace, pickupTime, dropoffTime, totalFare);
    }
}
