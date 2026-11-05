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

    @Column(name = "departure_place", nullable = false)
    private String departurePlace;

    @Column(name = "destination_place", nullable = false)
    private String destinationPlace;

    @Column(name = "dropoff_time")
    private Instant dropoffTime;

    private RideRecordEntity(Long matchId, String departurePlace, String destinationPlace, Instant dropoffTime) {
        this.matchId = matchId;
        this.departurePlace = departurePlace;
        this.destinationPlace = destinationPlace;
        this.dropoffTime = dropoffTime;
    }

    public static RideRecordEntity from(RideRecord rideRecord) {
        return new RideRecordEntity(rideRecord.getMatchId(), rideRecord.getDeparturePlace(), rideRecord.getDestinationPlace(),
                rideRecord.getDropoffTime());
    }

    public RideRecord toDomain() {
        return RideRecord.load(matchId, departurePlace, destinationPlace, dropoffTime);
    }
}
