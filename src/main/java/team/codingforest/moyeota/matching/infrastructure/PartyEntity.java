package team.codingforest.moyeota.matching.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.matching.domain.*;
import team.codingforest.moyeota.matching.domain.enums.MemberStatus;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Column(name = "estimated_fare", nullable = false)
    private Integer estimatedFare;

    @Column(name = "estimated_time", nullable = false)
    private Integer estimatedTime;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String route;

    @Column(name = "taxi_driver_id")
    private Long taxiDriverId;

    protected PartyEntity() {}

    private PartyEntity(Long hostId, Double departureLat, Double departureLng, Double destinationLat, Double destinationLng, String departure,
                       String destination, Integer capacity, Integer departureRadius, Integer destinationRadius, PartyStatus status,
                        Integer estimatedFare, Integer estimatedTime, String route, Long taxiDriverId) {
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
        this.estimatedFare = estimatedFare;
        this.estimatedTime = estimatedTime;
        this.route = route;
        this.taxiDriverId = taxiDriverId;
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
                party.getStatus(),
                party.getEstimatedFare(),
                party.getEstimatedTime(),
                party.getRoute(),
                party.getTaxiDriverId()
        );

        for(PartyMember member : party.getMembers()) {
            entity.members.add(PartyMemberEntity.of(entity, member));
        }

        return entity;
    }

    public Party toDomain() {
        return Party.restore(getId(), hostId, new Location(departureLat, departureLng), new Location(destinationLat, destinationLng),
                new Radius(departureRadius), new Radius(destinationRadius), departure, destination, new Capacity(capacity),
                members.stream().map(PartyMemberEntity::toDomain).toList(), getCreatedAt(), status, estimatedFare, estimatedTime, route, taxiDriverId);
    }

    public void update(Party party) {
        this.hostId = party.getHostId();
        this.status = party.getStatus();
        this.taxiDriverId = party.getTaxiDriverId();
        syncMembers(party.getMembers());
    }

    private void syncMembers(List<PartyMember> domainMembers) {
        Map<Long, PartyMember> byId = domainMembers.stream()
                .collect(Collectors.toMap(PartyMember::getMemberId, m->m));

        members.removeIf(e -> !byId.containsKey(e.getMemberId()));

        for(PartyMemberEntity e : members) {
            e.updateStatus(byId.get(e.getMemberId()).getStatus());
        }

        Set<Long> existing = members.stream()
                .map(PartyMemberEntity::getMemberId)
                .collect(Collectors.toSet());

        for(PartyMember m : domainMembers) {
            if(!existing.contains(m.getMemberId())) {
                members.add(PartyMemberEntity.of(this, m));
            }
        }
    }

}
