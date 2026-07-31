package team.codingforest.moyeota.matching.domain;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.common.BaseTimeEntity;

@Entity
@Getter
@Table(name = "match_room")
public class MatchRoom extends BaseTimeEntity {

    @Column(nullable = false)
    private Long hostId;

    @Column(nullable = false)
    private Double departureLatitude;

    @Column(nullable = false)
    private Double departureLongitude;

    @Column(nullable = false)
    private Double destinationLatitude;

    @Column(nullable = false)
    private Double destinationLongitude;

    @Column(nullable = false)
    private String departurePlace;

    @Column(nullable = false)
    private String destinationPlace;

    @Column(nullable = false)
    private Integer maxPassengers;

    @Column(nullable = false)
    private Double departureRadius;

    @Column(nullable = false)
    private Double destinationRadius;

    @Column(nullable = false)
    private Integer estimated_fare;

    @Column(nullable = false)
    private Integer estimated_time;
}
