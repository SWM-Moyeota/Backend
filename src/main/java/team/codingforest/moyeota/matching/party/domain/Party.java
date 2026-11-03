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
    private static final Duration MAX_CONFIRM_AHEAD = Duration.ofHours(1);
    // "도착했어요"를 받는 출발지 반경. 거리는 서비스가 이 값으로 DB(PostGIS)에 물어 잰다
    public static final double ARRIVAL_RADIUS_METERS = 100;
    // 앱이 "지금"을 고르면 요청이 도착할 때는 이미 지난 시각이라, 그만큼은 지난 시각도 받는다
    private static final Duration CONFIRM_CLOCK_SKEW = Duration.ofMinutes(1);
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
    // 예약 방은 만들 때 정한 출발 시각, 실시간 방은 방장이 확정한 약속 시간(확정 전에는 null). 바꾸는 길은 confirm 하나뿐이다
    private Instant departureAt;
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

        // 약속이 정해진 방(확정한 실시간 방, 모든 예약 방)은 방장도 나갈 수 없다 - 남은 사람이 약속 장소에서 기다리게 되기 때문
        if(isConfirmed()) throw new BusinessException(MatchingErrorCode.CANNOT_LEAVE_CONFIRMED_PARTY);

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

    /**
     *  실시간 방의 약속 시간 확정 - 방장이 채팅으로 정한 시간을 박는다. 장소는 늘 방의 출발지라 따로 담지 않는다.
     *  확정하면 모집도 끝난다: ACTIVE 면 COMPLETED 로 넘기고, 이미 정원이 차 COMPLETED 면 그대로 둔다.
     *  약속 시간은 확정하는 순간(now)부터 1시간 안이어야 한다
     */
    public void confirm(Long memberId, Instant departureAt, Instant now) {
        if(!hasMember(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);

        if(!isHost(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_HOST);

        if(isReservation()) throw new BusinessException(MatchingErrorCode.NOT_REALTIME_PARTY);

        // 상태 검사보다 먼저 - 기사 호출 모드에선 확정 직후 MATCHING 으로 넘어가 두 번 누르면 "모집 중 아님"이 아니라 "이미 확정"이어야 한다
        if(isConfirmed()) throw new BusinessException(MatchingErrorCode.ALREADY_CONFIRMED);

        ensureRecruiting();

        if(members.size() < MIN_MEMBERS_TO_DEPART) throw new BusinessException(MatchingErrorCode.NOT_ENOUGH_MEMBERS);

        if(departureAt == null || departureAt.isBefore(now.minus(CONFIRM_CLOCK_SKEW)) || departureAt.isAfter(now.plus(MAX_CONFIRM_AHEAD))) throw new BusinessException(MatchingErrorCode.INVALID_MEETING_TIME);

        this.departureAt = departureAt;

        if(status == PartyStatus.ACTIVE) completeRecruiting();

        version++;
    }

    /**
     *  약속이 정해진 방인가 - 따로 저장하지 않고 계산한다. 예약 방은 만들 때 출발 시각을 정하므로 처음부터 확정,
     *  실시간 방은 방장이 약속 시간을 확정해 departureAt 이 채워졌을 때
     */
    public boolean isConfirmed() {
        return isReservation() || departureAt != null;
    }

    /**
     *  출발지 도착 - 약속 시간(departureAt)부터 출발지 100m(ARRIVAL_RADIUS_METERS) 안에 있는 멤버가 누른다. 장소는 늘 방의 출발지다.
     *  거리는 도메인이 재지 않는다 - 서비스가 지금 위치가 반경 안인지 DB(PostGIS ST_DWithin)에 물어본 답을 nearDeparture 로 넘긴다.
     *  예약 방은 모집 정리 스케줄러가 돌기 전이면 출발 시각에도 ACTIVE 일 수 있어 COMPLETED 와 함께 받는다.
     *  이미 도착했으면 처음 시각을 그대로 두고 아무것도 바꾸지 않는다(위치·시간도 다시 보지 않는다)
     *
     *  @return 이번에 처음 도착했으면 true, 이미 도착해 있었으면 false
     */
    public boolean arrive(Long memberId, boolean nearDeparture, Instant now) {
        PartyMember member = findMember(memberId);

        ensureAttendanceOpen();

        if(member.isArrived()) return false;

        if(now.isBefore(departureAt)) throw new BusinessException(MatchingErrorCode.MEETING_NOT_STARTED);

        if(!nearDeparture) throw new BusinessException(MatchingErrorCode.TOO_FAR_FROM_DEPARTURE);

        member.arrive(now);
        version++;

        return true;
    }

    /**
     *  출발 - 도착한 멤버가 약속 시간 뒤에 누르면 방이 바로 끝난다(FINISHED). 방장이 아니어도 된다.
     *  그 순간 도착하지 않은 멤버는 모두 노쇼로 찍히고, 도착한 멤버가 실제로 탄 사람이다.
     *  예약 방은 아무도 들어오지 않았으면 모집 정리 때 취소되는 방이라 혼자 남은 방은 출발할 수 없다
     *
     *  @return 이번에 노쇼로 찍힌 memberId (합류 순)
     */
    public List<Long> depart(Long memberId, Instant now) {
        PartyMember member = findMember(memberId);

        ensureAttendanceOpen();

        if(taxiDriverId != null) throw new BusinessException(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);

        if(members.size() < MIN_MEMBERS_TO_DEPART) throw new BusinessException(MatchingErrorCode.NOT_ENOUGH_MEMBERS);

        if(now.isBefore(departureAt)) throw new BusinessException(MatchingErrorCode.MEETING_NOT_STARTED);

        if(!member.isArrived()) throw new BusinessException(MatchingErrorCode.NOT_ARRIVED);

        List<Long> noShowMemberIds = markNoShowsExceptArrived(now);

        status = PartyStatus.FINISHED;
        version++;

        return noShowMemberIds;
    }

    /**
     *  자동 정리 - 실시간 방은 만든 지 1일, 예약 방은 출발 시각에서 1일이 지나도 열려 있으면(ACTIVE·COMPLETED) 정리 스케줄러가 닫는다.
     *  언제 닫을지는 스케줄러 조회가 가르고, 여기서는 어떻게 닫을지를 정한다.
     *  약속이 정해진 방은 출발(depart)과 같은 기준으로 노쇼를 가른다
     *   - 도착한 멤버가 있으면 FINISHED, 도착하지 않은 멤버는 노쇼다. 도착한 멤버가 실제로 탄 사람이다
     *   - 아무도 도착하지 않았으면 합승이 없었으니 CANCELED, 멤버 전원이 노쇼다. 도착 버튼을 누르지 않은 것도 본인 몫이고 이의는 운영자가 노쇼를 무효로 돌려 받는다
     *   - 함께 탈 사람이 들어오지 않은 예약 방(멤버 1명)은 모집 정리와 같이 노쇼 없이 CANCELED - 출발도 할 수 없던 방이다
     *  약속을 정하지 않은 실시간 방은 노쇼를 가릴 약속이 없다 - COMPLETED 는 이전 자동 종료처럼 FINISHED(구버전 앱), ACTIVE 는 CANCELED.
     *  조회와 행 잠금 사이에 출발·나가기로 이미 닫혔거나 기사 호출 단계로 넘어간 방은 PARTY_CLOSED 로 거절한다(스케줄러는 건너뛴다)
     */
    public AutoCloseResult autoClose(Instant now) {
        if(!status.isRecruiting()) throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);

        if(taxiDriverId != null) throw new BusinessException(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);

        // 만든 지 1일이 다 돼 확정한 실시간 방은 약속이 그 뒤일 수 있다 - 만나기도 전에 전원을 노쇼로 찍지 않게 약속 시간이 지난 다음 정리에서 닫는다
        if(isConfirmed() && now.isBefore(departureAt)) throw new BusinessException(MatchingErrorCode.MEETING_NOT_STARTED);

        List<Long> noShowMemberIds = List.of();

        if(!isConfirmed()) {
            status = status == PartyStatus.COMPLETED ? PartyStatus.FINISHED : PartyStatus.CANCELED;
        } else if(members.size() < MIN_MEMBERS_TO_DEPART) {
            status = PartyStatus.CANCELED;
        } else {
            noShowMemberIds = markNoShowsExceptArrived(now);
            status = arrivedCount() > 0 ? PartyStatus.FINISHED : PartyStatus.CANCELED;
        }

        version++;

        return new AutoCloseResult(status, arrivedCount(), noShowMemberIds);
    }

    /**
     *  도착하지 않은 멤버를 모두 노쇼로 찍는다 - 출발(depart)과 자동 정리(autoClose)가 같은 기준을 쓴다
     *
     *  @return 이번에 노쇼로 찍힌 memberId (합류 순)
     */
    private List<Long> markNoShowsExceptArrived(Instant now) {
        List<Long> noShowMemberIds = new ArrayList<>();
        for(PartyMember m : members) {
            if(m.isArrived()) continue;

            m.markNoShow(now);
            noShowMemberIds.add(m.getMemberId());
        }

        return List.copyOf(noShowMemberIds);
    }

    private int arrivedCount() {
        return (int) members.stream().filter(PartyMember::isArrived).count();
    }

    /**
     *  도착·출발을 받을 수 있는 방인가 - 닫히지 않았고(ACTIVE/COMPLETED) 약속이 정해져 있어야 한다
     */
    private void ensureAttendanceOpen() {
        if(!status.isRecruiting()) throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);

        if(!isConfirmed()) throw new BusinessException(MatchingErrorCode.PARTY_NOT_CONFIRMED);
    }

    private PartyMember findMember(Long memberId) {
        for(PartyMember m : members) {
            if(m.getMemberId().equals(memberId)) return m;
        }

        throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);
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

    /**
     *  약속을 확정하지 않은 방을 방장이 끝내는 이전 경로(구버전 앱). 노쇼 없이 멤버 전원이 탄 것으로 남는다.
     *  약속이 정해진 방(확정한 실시간 방, 모든 예약 방)은 출발(depart)로만 끝낸다 - 끝내는 길이 둘이면 노쇼 기록을 건너뛸 수 있다.
     *  예약 방은 늘 확정이라 여기까지 오지 않는다
     */
    public void finishWithoutDriver(Long memberId) {
        if(!hasMember(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);

        // 방장 검사보다 먼저 - 확정된 방에선 방장이 아니어도 출발 버튼으로 끝낼 수 있으니 그 길을 알려 준다
        if(isConfirmed()) throw new BusinessException(MatchingErrorCode.USE_DEPART_FOR_CONFIRMED_PARTY);

        if(!isHost(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_HOST);

        if(status != PartyStatus.COMPLETED) throw new BusinessException(MatchingErrorCode.PARTY_NOT_COMPLETED);

        if(taxiDriverId != null) throw new BusinessException(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);

        status = PartyStatus.FINISHED;
        version++;
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
