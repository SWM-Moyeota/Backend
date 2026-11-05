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

// 택시 시절 컬럼(기사 id, 탑승 시각, 실제 요금)은 테이블에 남아 있지만 매핑하지 않는다 - 모두 nullable 이라 INSERT 때 null 로 들어간다.
// 컬럼 DROP 은 이 매핑을 지운 코드가 모든 서버와 롤백 대상에 깔린 다음 릴리스에서 한다
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
