package team.codingforest.moyeota.matching.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.matching.domain.*;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

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

    protected PartyEntity() {}

    private PartyEntity(Long hostId, Double departureLat, Double departureLng, Double destinationLat, Double destinationLng, String departure,
                       String destination, Integer capacity, Integer departureRadius, Integer destinationRadius, PartyStatus status) {
        this.hostId = hostId;
        this.departureLat = departureLat;
        this.departureLng = departureLng;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.departure = departure;
        this.destination = destination;
        this.capacity = capacity;
        this.departureRadius = departureRadius;
        this.destinationRadius = destinationRadius;
        this.status = status;
    }

    public static PartyEntity from(Party party) {
        PartyEntity entity = new PartyEntity(
                party.getHostId(),
                party.getDepartureLocation().latitude(),
                party.getDepartureLocation().longitude(),
                party.getDestinationLocation().latitude(),
                party.getDestinationLocation().longitude(),
                party.getDeparture(),
                party.getDestination(),
                party.getCapacity().value(),
                party.getDepartureRadius().meters(),
                party.getDestinationRadius().meters(),
                party.getStatus()
        );

        for(PartyMember member : party.getMembers()) {
            entity.members.add(PartyMemberEntity.of(entity, member));
        }

        return entity;
    }

    public Party toDomain() {
        return Party.restore(getId(), hostId, new Location(departureLat, departureLng), new Location(destinationLat, destinationLng),
                new Radius(departureRadius), new Radius(destinationRadius), departure, destination, new Capacity(capacity),
                members.stream().map(PartyMemberEntity::toDomain).toList(), getCreatedAt(), status);
    }
}
