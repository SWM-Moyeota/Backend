package team.codingforest.moyeota.matching.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.matching.domain.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "match_room")
public class PartyEntity extends BaseTimeEntity {

    @Column(nullable = false)
    private Long hostId;

    @Column(nullable = false)
    private Double departureLat;

    @Column(nullable = false)
    private Double departureLng;

    @Column(nullable = false)
    private Double destinationLat;

    @Column(nullable = false)
    private Double destinationLng;

    @Column(nullable = false)
    private String departure;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private Integer departureRadius;

    @Column(nullable = false)
    private Integer destinationRadius;

    @OneToMany(mappedBy = "party", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PartyMemberEntity> members = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private PartyStatus status;
}
