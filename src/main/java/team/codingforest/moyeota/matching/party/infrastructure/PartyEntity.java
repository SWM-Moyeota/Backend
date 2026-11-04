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
import lombok.Getter;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;

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
public class PartyEntity extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    @OrderBy("joinedAt ASC, memberId ASC")
    private List<PartyMemberEntity> members = new ArrayList<>();

    // 멤버가 없는 방(마지막 멤버가 나가 취소된 방)은 방장도 없어 NULL
    @Column(name = "host_id")
    private Long hostId;

    @Enumerated(EnumType.STRING)
    private PartyStatus status;

    @Column(name = "estimated_fare")
    private Integer estimatedFare;

    @Column(name = "estimated_time")
    private Integer estimatedTime;

    @Column(columnDefinition = "TEXT")
    private String route;

    @Column(name = "taxi_driver_id")
    private Long taxiDriverId;

    @Column(name = "match_started_at")
    private Instant matchingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private RoomType roomType;

    @Column(name = "departure_at")
    private Instant departureAt;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "meeting_notified_at")
    private Instant meetingNotifiedAt;

    // 컬럼이 smallint(0..4)라 short 로 맞춘다 - int 로 두면 ddl-auto=validate 가 integer 를 기대해 기동이 실패한다
    @Column(name = "departure_reminder_count", nullable = false)
    private short departureReminderCount;

    protected PartyEntity() {}

    private PartyEntity(Double departureLat, Double departureLng, Double destinationLat, Double destinationLng, String departure,
                       String destination, Integer capacity, Integer departureRadius, Integer destinationRadius, Long hostId, PartyStatus status,
                        Integer estimatedFare, Integer estimatedTime, String route, Long taxiDriverId, Instant matchingStartedAt, Instant completedAt, long version,
                        RoomType roomType, Instant departureAt, Instant reminderSentAt, Instant meetingNotifiedAt, int departureReminderCount) {
        this.departureLat = departureLat;
        this.departureLng = departureLng;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.departure = departure;
        this.destination = destination;
        this.capacity = capacity;
        this.departureRadius = departureRadius;
        this.destinationRadius = destinationRadius;
        this.hostId = hostId;
        this.status = status;
        this.estimatedFare = estimatedFare;
        this.estimatedTime = estimatedTime;
        this.route = route;
        this.taxiDriverId = taxiDriverId;
        this.matchingStartedAt = matchingStartedAt;
        this.completedAt = completedAt;
        this.version = version;
        this.roomType = roomType;
        this.departureAt = departureAt;
        this.reminderSentAt = reminderSentAt;
        this.meetingNotifiedAt = meetingNotifiedAt;
        this.departureReminderCount = (short) departureReminderCount;
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
                party.getDepartureRadius().meters(),
                party.getDestinationRadius().meters(),
                party.hostId(),
                party.getStatus(),
                party.getEstimatedFare(),
                party.getEstimatedTime(),
                party.getRoute(),
                party.getTaxiDriverId(),
                party.getMatchingStartedAt(),
                party.getCompletedAt(),
                party.getVersion(),
                party.getRoomType(),
                party.getDepartureAt(),
                party.getReminderSentAt(),
                party.getMeetingNotifiedAt(),
                party.getDepartureReminderCount()
        );

        for(PartyMember member : party.getMembers()) {
            entity.members.add(PartyMemberEntity.of(entity, member));
        }

        return entity;
    }

    public Party toDomain() {
        return Party.load(getId(), new Location(departureLat, departureLng), new Location(destinationLat, destinationLng),
                new Radius(departureRadius), new Radius(destinationRadius), departure, destination, new Capacity(capacity),
                members.stream().map(PartyMemberEntity::toDomain).toList(), hostId, getCreatedAt(), status, estimatedFare, estimatedTime, route, taxiDriverId, matchingStartedAt, completedAt, version, roomType, departureAt, reminderSentAt,
                meetingNotifiedAt, departureReminderCount);
    }

    public void update(Party party) {
        this.hostId = party.hostId();   // 방장이 나가면 승계된다
        this.status = party.getStatus();
        this.taxiDriverId = party.getTaxiDriverId();
        this.matchingStartedAt = party.getMatchingStartedAt();
        this.completedAt = party.getCompletedAt();
        this.version = party.getVersion();
        this.departureAt = party.getDepartureAt();   // 실시간 방은 방장이 약속 시간을 확정하면 채워진다
        this.reminderSentAt = party.getReminderSentAt();
        this.meetingNotifiedAt = party.getMeetingNotifiedAt();
        this.departureReminderCount = (short) party.getDepartureReminderCount();
        syncMembers(party.getMembers());
    }

    private void syncMembers(List<PartyMember> domainMembers) {
        Map<Long, PartyMember> byId = domainMembers.stream()
                .collect(Collectors.toMap(PartyMember::getMemberId, m->m));

        members.removeIf(e -> !byId.containsKey(e.getMemberId()));

        Set<Long> existing = new HashSet<>();

        // 남아 있는 멤버 행도 도착·노쇼가 바뀌었을 수 있다 - 더티체킹으로 반영된다
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
