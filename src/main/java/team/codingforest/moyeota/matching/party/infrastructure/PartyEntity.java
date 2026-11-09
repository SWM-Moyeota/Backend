package team.codingforest.moyeota.matching.party.infrastructure;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@Table(name = "match_room",
        indexes = @Index(name = "idx_match_room_status_location", columnList = "status, departure_lat, departure_lng"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyEntity extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

	@Column(name = "host_id")
	private Long hostId;

    @Column(nullable = false)
    private Double departureLat;

    @Column(nullable = false)
    private Double departureLng;

    @Column(nullable = false)
    private Double destinationLat;

    @Column(nullable = false)
    private Double destinationLng;

    @Column(nullable = false, name = "departure_place")
    private String departure;

    @Column(nullable = false, name = "destination_place")
    private String destination;

    @Column(nullable = false)
    private Integer capacity;

    @OneToMany(mappedBy = "party", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, memberId ASC")
    private List<PartyMemberEntity> members = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private PartyStatus status;

    @Column(name = "estimated_fare")
    private Integer estimatedFare;

    @Column(name = "estimated_time")
    private Integer estimatedTime;

    @Column(columnDefinition = "TEXT")
    private String route;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private RoomType roomType;

    @Column(name = "departure_at")
    private Instant departureAt;

	@Column(nullable = false)
	private long version;

    private PartyEntity(Double departureLat, Double departureLng, Double destinationLat, Double destinationLng, String departure,
                       String destination, Integer capacity, Long hostId, PartyStatus status,
                        Integer estimatedFare, Integer estimatedTime, String route, long version,
                        RoomType roomType, Instant departureAt) {
        this.departureLat = departureLat;
        this.departureLng = departureLng;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.departure = departure;
        this.destination = destination;
        this.capacity = capacity;
        this.hostId = hostId;
        this.status = status;
        this.estimatedFare = estimatedFare;
        this.estimatedTime = estimatedTime;
        this.route = route;
        this.version = version;
        this.roomType = roomType;
        this.departureAt = departureAt;
    }

    public static PartyEntity from(Party party) {
        PartyEntity entity = new PartyEntity(
                party.getDepartureLocation().latitude(),
                party.getDepartureLocation().longitude(),
                party.getDestinationLocation().latitude(),
                party.getDestinationLocation().longitude(),
                party.getDeparture(),
                party.getDestination(),
                party.getCapacity().value(),
                party.getHostId(),
                party.getStatus(),
                party.getEstimatedFare(),
                party.getEstimatedTime(),
                party.getRoute(),
                party.getVersion(),
                party.getRoomType(),
                party.getDepartureAt()
        );

        for(PartyMember member : party.getMembers()) {
            entity.members.add(PartyMemberEntity.of(entity, member));
        }

        return entity;
    }

    public Party toDomain() {
        return Party.load(getId(), new Location(departureLat, departureLng), new Location(destinationLat, destinationLng),
                departure, destination, new Capacity(capacity),
                members.stream().map(PartyMemberEntity::toDomain).toList(), hostId, getCreatedAt(), status, estimatedFare, estimatedTime, route, version, roomType, departureAt);
    }

    public void update(Party party) {
        this.hostId = party.getHostId();   // 방장이 나가면 변경됨
        this.departureLat = party.getDepartureLocation().latitude();   // 약속 확정 때 만남 장소로 바뀔 수 있음
        this.departureLng = party.getDepartureLocation().longitude();
        this.departure = party.getDeparture();
        this.status = party.getStatus();
        this.version = party.getVersion();
        this.departureAt = party.getDepartureAt();   // 실시간 방은 방장이 약속 시간을 확정하면 채워짐
        syncMembers(party.getMembers());
    }

    private void syncMembers(List<PartyMember> domainMembers) {
        Map<Long, PartyMember> byId = domainMembers.stream()
                .collect(Collectors.toMap(PartyMember::getMemberId, m->m));

        members.removeIf(e -> !byId.containsKey(e.getMemberId()));

        Set<Long> existing = new HashSet<>();

        for(PartyMemberEntity e : members) {
            e.update(byId.get(e.getMemberId()));
            existing.add(e.getMemberId());
        }

        for(PartyMember m : domainMembers) {
            if(!existing.contains(m.getMemberId())) {
                members.add(PartyMemberEntity.of(this, m));
            }
        }
    }
}
