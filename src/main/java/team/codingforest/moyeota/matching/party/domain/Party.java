package team.codingforest.moyeota.matching.party.domain;

import lombok.Getter;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 *  애그리거트 루트
  */
// TODO - 예외처리 해야함
public class Party {
    private static final Duration MAX_RESERVATION_AHEAD = Duration.ofDays(7);
    private static final Duration FINISHABLE_BEFORE_DEPARTURE = Duration.ofMinutes(30);
    private static final int MIN_MEMBERS_TO_DEPART = 2;

    private final Long id;
    private final Location departureLocation;
    private final Location destinationLocation;
    private final String departure;
    private final String destination;
    private final Capacity capacity;
    private final Radius departureRadius;
    private final Radius destinationRadius;
    private final List<PartyMember> members;
    private Long hostId;
    private final Instant createdAt;
    private PartyStatus status;
    private final Integer estimatedFare;
    private final Integer estimatedTime;
    private final String route;
    private Long taxiDriverId;
    private Instant matchingStartedAt;
    private Instant completedAt;
    private long version;
    private final RoomType roomType;
    private final Instant departureAt;
    private Instant reminderSentAt;

    /**
     * 규칙 생성 -> 아무대서 Party 객체를 생성할 수 없게 하기
     */
    private Party(Long id, Location departureLocation, Location destinationLocation, Radius departureRadius, Radius destinationRadius
            ,String departure, String destination, Capacity capacity, List<PartyMember> members, Long hostId, Instant createdAt, PartyStatus status,
                  Integer estimatedFare, Integer estimatedTime, String route, Long taxiDriverId, Instant matchingStartedAt, Instant completedAt, long version,
                  RoomType roomType, Instant departureAt, Instant reminderSentAt) {
        this.id = id;
        this.departureLocation = departureLocation;
        this.destinationLocation = destinationLocation;
        this.departure = departure;
        this.destination = destination;
        this.capacity = capacity;
        this.departureRadius = departureRadius;
        this.destinationRadius = destinationRadius;
        this.members = new ArrayList<>(members);
        this.hostId = hostId;
        this.createdAt = createdAt;
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
    }

    /**
     *  매칭방 생성
     */
    public static Party open(Long creatorId, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity,
                             Instant createdAt, Radius departureRadius, Radius destinationRadius, Integer estimatedFare, Integer estimatedTime, String route) {
        if(departureLocation.equals(destinationLocation)) throw new BusinessException(MatchingErrorCode.SAME_DEPARTURE_DESTINATION);

        if(estimatedFare == null || estimatedFare < 0) throw new BusinessException(MatchingErrorCode.INVALID_ROUTE_ESTIMATE);

        if(estimatedTime == null || estimatedTime < 0) throw new BusinessException(MatchingErrorCode.INVALID_ROUTE_ESTIMATE);

        if(route == null) throw new BusinessException(MatchingErrorCode.INVALID_ROUTE_ESTIMATE);

        Party party = new Party(null, departureLocation, destinationLocation, departureRadius, destinationRadius, departure, destination, capacity, new ArrayList<>(), creatorId, createdAt, PartyStatus.ACTIVE,
                estimatedFare, estimatedTime, route, null, null, null, 0L, RoomType.REALTIME, null, null);

        party.members.add(new PartyMember(creatorId, Instant.now()));

        if(party.isFull()) {
            party.status = PartyStatus.COMPLETED;
            party.completedAt = Instant.now();
        }

        return party;
    }

    public static Party reserve(Long creatorId, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity,
                                Instant createdAt, Instant departureAt) {
        if(departureLocation.equals(destinationLocation)) throw new BusinessException(MatchingErrorCode.SAME_DEPARTURE_DESTINATION);

        if(!departureAt.isAfter(createdAt) || departureAt.isAfter(createdAt.plus(MAX_RESERVATION_AHEAD))) throw new BusinessException(MatchingErrorCode.INVALID_DEPARTURE_TIME);

        Party party = new Party(null, departureLocation, destinationLocation, Radius.DEFAULT, Radius.DEFAULT, departure, destination, capacity, new ArrayList<>(), creatorId, createdAt, PartyStatus.ACTIVE,
                null, null, null, null, null, null, 0L, RoomType.RESERVED, departureAt, null);

        party.members.add(new PartyMember(creatorId, Instant.now()));

        return party;
    }

    /**
     *  매칭방 참여
     */
    public void join(Long memberId) {
        if(status != PartyStatus.ACTIVE) throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
        if(isReservation() && !Instant.now().isBefore(departureAt)) throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
        if(members.size() >= capacity.value()) throw new BusinessException(MatchingErrorCode.PARTY_FULL);
        if(hasMember(memberId)) throw new BusinessException(MatchingErrorCode.ALREADY_JOINED_PARTY);

        members.add(new PartyMember(memberId, Instant.now()));
        version++;

        if(isFull()) {
            status = PartyStatus.COMPLETED;
            completedAt = Instant.now();
        }
    }

    /**
     *  매칭방 나가기
     */
    public void leave(Long memberId) {
        if(!hasMember(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);
        ensureRecruiting();

        PartyMember tmp = null;
        version++;

        // 나가는 멤버 찾기
        for(PartyMember m : members) {
            if(m.getMemberId().equals(memberId)) {
                tmp = m;
                break;
            }
        }

        members.remove(tmp);

        // 마지막 사용자도 나간 경우 방 없애기 - 남은 멤버가 없으니 방장도 없다
        if(members.isEmpty()) {
            cancel();
            hostId = null;
            return;
        }

        // 방장이 나가면 남은 멤버 중 가장 먼저 합류한 사람이 승계한다
        if(memberId.equals(hostId)) {
            hostId = earliestJoinedMemberId(members);
        }

        status = PartyStatus.ACTIVE;
        completedAt = null;
    }

    public void closeRecruiting(Long memberId) {
        if(!hasMember(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);

        if(!isHost(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_HOST);

        if(isReservation()) throw new BusinessException(MatchingErrorCode.NOT_REALTIME_PARTY);

        if(status != PartyStatus.ACTIVE) throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);

        if(members.size() < MIN_MEMBERS_TO_DEPART) throw new BusinessException(MatchingErrorCode.NOT_ENOUGH_MEMBERS);

        completeRecruiting();
        version++;
    }

    public void expireRecruiting() {
        if(status != PartyStatus.ACTIVE) throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);

        if(members.size() < MIN_MEMBERS_TO_DEPART) {
            status = PartyStatus.CANCELED;
        } else {
            completeRecruiting();
        }

        version++;
    }

    public void markReminderSent(Instant now) {
        reminderSentAt = now;
    }

    public boolean isReservation() {
        return roomType == RoomType.RESERVED;
    }

    private void completeRecruiting() {
        status = PartyStatus.COMPLETED;
        completedAt = Instant.now();
    }

    /**
     *  유저가 해당 방에 있는지 유뮤 확인
     */
    public boolean hasMember(Long memberId) {
        for(PartyMember m : members) {
            if(m.getMemberId().equals(memberId)) return true;
        }

        return false;
    }

    /**
     *  이미 꽉찬 방인지 확인
     */
    public boolean isFull() {
        return members.size() == capacity.value();
    }

    /**
     *  영속 복원용. 저장된 방장이 비었거나(구버전 서버가 만든 방) 현재 멤버가 아니면(구버전 서버가 처리한 퇴장) 승계 규칙으로 다시 정한다.
     *  배포 중 구버전과 섞여 도는 동안에만 생길 수 있는 어긋남이고, 다음 저장 때 컬럼도 이 값으로 고쳐진다
     */
    public static Party load(Long id, Location departureLocation, Location destinationLocation, Radius departureRadius, Radius destinationRadius, String departure, String destination, Capacity capacity, List<PartyMember> members, Long hostId, Instant createdAt, PartyStatus status,
                             Integer estimatedFare, Integer estimatedTime, String route, Long taxiDriverId, Instant matchingStartedAt, Instant completedAt, long version,
                             RoomType roomType, Instant departureAt, Instant reminderSentAt) {
        return new Party(id, departureLocation, destinationLocation, departureRadius, destinationRadius, departure, destination, capacity, members, restoreHostId(members, hostId), createdAt, status, estimatedFare, estimatedTime, route, taxiDriverId, matchingStartedAt, completedAt, version, roomType, departureAt, reminderSentAt);
    }

    /**
     *  host_id 컬럼이 생기기 전 모양(실시간 방). 방장은 승계 규칙(= V25 백필 규칙)으로 멤버 목록에서 정한다
     */
    public static Party load(Long id, Location departureLocation, Location destinationLocation, Radius departureRadius, Radius destinationRadius, String departure, String destination, Capacity capacity, List<PartyMember> members, Instant createdAt, PartyStatus status,
                                    Integer estimatedFare, Integer estimatedTime, String route, Long taxiDriverId, Instant matchingStartedAt, Instant completedAt, long version) {
        return load(id, departureLocation, destinationLocation, departureRadius, destinationRadius, departure, destination, capacity, members, null, createdAt, status, estimatedFare, estimatedTime, route, taxiDriverId, matchingStartedAt, completedAt, version, RoomType.REALTIME, null, null);
    }

    private static Long restoreHostId(List<PartyMember> members, Long storedHostId) {
        boolean stillMember = storedHostId != null && members.stream().anyMatch(m -> m.getMemberId().equals(storedHostId));

        return stillMember ? storedHostId : earliestJoinedMemberId(members);
    }

    /**
     *  승계 규칙 - 가장 먼저 합류한 멤버, 합류 시각이 같으면 memberId 가 작은 멤버. 멤버가 없으면 null
     */
    private static Long earliestJoinedMemberId(List<PartyMember> members) {
        return members.stream()
                .min(Comparator.comparing(PartyMember::getJoinedAt)
                        .thenComparing(PartyMember::getMemberId))
                .map(PartyMember::getMemberId)
                .orElse(null);
    }

    /**
     *  혼자 남은 사람이 나가면 방 폭파 - leave 내부에서만 호출됨
     */
    private void cancel() {
        ensureRecruiting();

        status = PartyStatus.CANCELED;
    }

    public void assignDriver(Long driverId) {
        if(status != PartyStatus.MATCHING) throw new BusinessException(MatchingErrorCode.PARTY_NOT_MATCHING);

        if(taxiDriverId != null) throw new BusinessException(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);

        this.taxiDriverId = driverId;
        this.status = PartyStatus.DRIVER_ASSIGNED;
    }

    public void startMatching(Instant now) {
        if(status != PartyStatus.COMPLETED) throw new BusinessException(MatchingErrorCode.PARTY_NOT_COMPLETED);

        status = PartyStatus.MATCHING;
        matchingStartedAt = now;
    }

    public void startRide(Long driverId) {
        ensureAssignDriver(driverId);
        if(status != PartyStatus.DRIVER_ASSIGNED) throw new BusinessException(MatchingErrorCode.NOT_AWAITING_PICKUP);

        status = PartyStatus.IN_RIDE;
    }

    public void completeRide(Long driverId, int fare) {
        ensureAssignDriver(driverId);
        if(status != PartyStatus.IN_RIDE) throw new BusinessException(MatchingErrorCode.NOT_RIDING);

        // TODO 결제쪽이 완료된 후 완성

        status = PartyStatus.FINISHED;
    }

    /**
     *      기사를 못 구한 방 폭파
     */
    public void failMatching() {
        if(status != PartyStatus.MATCHING) throw new BusinessException(MatchingErrorCode.PARTY_NOT_MATCHING);

        if(taxiDriverId != null) throw new BusinessException(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);

        status = PartyStatus.CANCELED;
    }

    /**
     *  배정된 기사가 픽업하러 오는 중인가 - "기사 도착" 통보가 유효한 유일한 구간
     */
    public boolean isAwaitingPickup(Long driverId) {
        return status == PartyStatus.DRIVER_ASSIGNED && driverId.equals(taxiDriverId);
    }

    /**
     *  방장 userId (match_room.host_id). 방을 만든 사람이 방장이고, 방장이 나가면 남은 멤버 중 가장 먼저 합류한 사람이 승계한다.
     *  마지막 멤버까지 나가 멤버가 없는 방은 null
     */
    public Long hostId() {
        return hostId;
    }

    /** 방장 판별은 이 메소드로만 한다 - 밖에서 hostId 를 꺼내 비교하지 않기 */
    public boolean isHost(Long memberId) {
        return hostId != null && hostId.equals(memberId);
    }

    public void finishWithoutDriver(Long memberId) {
        if(!hasMember(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);

        if(!isHost(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_HOST);

        if(status != PartyStatus.COMPLETED) throw new BusinessException(MatchingErrorCode.PARTY_NOT_COMPLETED);

        if(taxiDriverId != null) throw new BusinessException(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);

        if(isReservation() && Instant.now().isBefore(departureAt.minus(FINISHABLE_BEFORE_DEPARTURE))) throw new BusinessException(MatchingErrorCode.RESERVATION_NOT_STARTED);

        status = PartyStatus.FINISHED;
        version++;
    }

    public void expireCompleted() {
        ensureFinishableWithoutDriver();
        status = PartyStatus.FINISHED;
        version++;
    }

    private void ensureFinishableWithoutDriver() {
        if(status != PartyStatus.COMPLETED) throw new BusinessException(MatchingErrorCode.PARTY_NOT_COMPLETED);

        if(taxiDriverId != null) throw new BusinessException(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);
    }

    private void ensureAssignDriver(Long driverId) {
        if(taxiDriverId == null || !taxiDriverId.equals(driverId)) throw new BusinessException(MatchingErrorCode.NOT_ASSIGNED_DRIVER);
    }

    private void ensureRecruiting() {
        if(!status.isRecruiting()) throw new BusinessException(MatchingErrorCode.PARTY_NOT_RECRUITING);
    }

	public Long getId() {
		return id;
	}

	public Location getDepartureLocation() {
		return departureLocation;
	}

	public Location getDestinationLocation() {
		return destinationLocation;
	}

	public String getDeparture() {
		return departure;
	}

	public String getDestination() {
		return destination;
	}

	public Capacity getCapacity() {
		return capacity;
	}

	public Radius getDepartureRadius() {
		return departureRadius;
	}

	public Radius getDestinationRadius() {
		return destinationRadius;
	}

	public List<PartyMember> getMembers() {
		return members;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public PartyStatus getStatus() {
		return status;
	}

	public Integer getEstimatedFare() {
		return estimatedFare;
	}

	public Integer getEstimatedTime() {
		return estimatedTime;
	}

	public String getRoute() {
		return route;
	}

	public Long getTaxiDriverId() {
		return taxiDriverId;
	}

	public Instant getMatchingStartedAt() {
		return matchingStartedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public long getVersion() {
		return version;
	}

	public RoomType getRoomType() {
		return roomType;
	}

	public Instant getDepartureAt() {
		return departureAt;
	}

	public Instant getReminderSentAt() {
		return reminderSentAt;
	}
}
