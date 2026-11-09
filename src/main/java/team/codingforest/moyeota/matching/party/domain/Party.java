package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *  동승 방 애그리거트 루트. 상태 전이는 PartyStatus 주석의 표를 따른다.
 *
 *  공개 메서드는 같은 순서로 검사한다 - 멤버 → 방장 → 방 열림 → 방 종류·확정 → 인원 → 시간 → 행위자 상태.
 *  같은 조건은 같은 헬퍼(findMember·ensureHost·ensureOpen·ensureConfirmed·ensureCompanion)로만 검사하고,
 *  지금 시각은 인자(now)로 받는다. 정상적인 건너뛰기는 결과로 돌려주고 예외는 규칙 위반에만 쓴다
 */
public class Party {
    /** 약속 시간은 확정하는 지금부터 이만큼 뒤부터 - "지금"을 약속으로 잡아 바로 출발해 남을 떨궈 내지 못하게 한다 */
    public static final Duration MIN_CONFIRM_AHEAD = Duration.ofMinutes(5);
    /** 실시간 방은 모이면 바로 타는 방이라 1시간 넘게 뒤는 예약 방으로 만든다 */
    public static final Duration MAX_CONFIRM_AHEAD = Duration.ofHours(1);
    public static final Duration MAX_RESERVATION_AHEAD = Duration.ofDays(7);
    public static final double ARRIVAL_RADIUS_METERS = 100;
    public static final Duration ARRIVAL_OPENS_BEFORE = Duration.ofMinutes(10);
    public static final int MIN_MEMBERS_TO_DEPART = 2;
    /** 약속 시각 알림이 이만큼 넘게 늦으면(서버가 멈췄다 다시 돈 경우) "약속 시간이에요"가 맞지 않아 보내지 않는다 */
    static final Duration MEETING_NOTICE_STALE_AFTER = Duration.ofMinutes(30);
    /**
     *  출발 재촉 시각 - 약속 시각에서 이만큼 지난 때. n 번째 재촉(0부터)은 departureAt + OFFSETS[n].
     *  개수를 바꾸면 PartyJpaRepository.findDepartureReminderTargetIds 도 함께 바꾼다(차례마다 기준 시각을 하나씩 받는다)
     */
    public static final List<Duration> DEPARTURE_REMINDER_OFFSETS = List.of(
            Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(4));

    private final Long id;
    private Location departureLocation;
    private final Location destinationLocation;
    private String departure;
    private final String destination;
    private final Capacity capacity;
    private final List<PartyMember> members;
    private Long hostId;
    private final Instant createdAt;
    private PartyStatus status;
    private final Integer estimatedFare;
    private final Integer estimatedTime;
    private final String route;
    private long version;
    private final RoomType roomType;
    /** 예약 방은 만들 때 정한 출발 시각, 실시간 방은 방장이 확정한 약속 시간(확정 전 null). 실시간 방은 이 값이 있을 때만 COMPLETED 다 */
    private Instant departureAt;
    private Instant reminderSentAt;
    private Instant meetingNotifiedAt;
    private int departureReminderCount;

    private Party(Long id, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity,
                  List<PartyMember> members, Long hostId, Instant createdAt, PartyStatus status,
                  Integer estimatedFare, Integer estimatedTime, String route, long version,
                  RoomType roomType, Instant departureAt, Instant reminderSentAt, Instant meetingNotifiedAt, int departureReminderCount) {
        this.id = id;
        this.departureLocation = departureLocation;
        this.destinationLocation = destinationLocation;
        this.departure = departure;
        this.destination = destination;
        this.capacity = capacity;
        this.members = new ArrayList<>(members);
        this.hostId = hostId;
        this.createdAt = createdAt;
        this.status = status;
        this.estimatedFare = estimatedFare;
        this.estimatedTime = estimatedTime;
        this.route = route;
        this.version = version;
        this.roomType = roomType;
        this.departureAt = departureAt;
        this.reminderSentAt = reminderSentAt;
        this.meetingNotifiedAt = meetingNotifiedAt;
        this.departureReminderCount = departureReminderCount;
    }


    public static Party create(Long creatorId, Location departureLocation, Location destinationLocation, String departure, String destination,
                               Capacity capacity, Instant now, Integer estimatedFare, Integer estimatedTime, String route) {
        ensureDifferentPlaces(departureLocation, destinationLocation);

        if(estimatedFare == null || estimatedFare < 0 || estimatedTime == null || estimatedTime < 0 || route == null) {
            throw new BusinessException(MatchingErrorCode.INVALID_ROUTE_ESTIMATE);
        }

        return open(creatorId, departureLocation, destinationLocation, departure, destination, capacity, now,
                estimatedFare, estimatedTime, route, RoomType.REALTIME, null);
    }

    public static Party reserve(Long creatorId, Location departureLocation, Location destinationLocation, String departure, String destination,
                                Capacity capacity, Instant now, Instant departureAt) {
        ensureDifferentPlaces(departureLocation, destinationLocation);

        if(!departureAt.isAfter(now) || departureAt.isAfter(now.plus(MAX_RESERVATION_AHEAD))) {
            throw new BusinessException(MatchingErrorCode.INVALID_DEPARTURE_TIME);
        }

        return open(creatorId, departureLocation, destinationLocation, departure, destination, capacity, now,
                null, null, null, RoomType.RESERVED, departureAt);
    }

    private static Party open(Long creatorId, Location departureLocation, Location destinationLocation, String departure, String destination,
                              Capacity capacity, Instant now, Integer estimatedFare, Integer estimatedTime, String route,
                              RoomType roomType, Instant departureAt) {
        Party party = new Party(null, departureLocation, destinationLocation, departure, destination, capacity, List.of(), creatorId, now,
                PartyStatus.ACTIVE, estimatedFare, estimatedTime, route, 0L, roomType, departureAt, null, null, 0);

        party.members.add(PartyMember.join(creatorId, now));

        return party;
    }

    /** 영속 복원 - 저장된 값을 그대로 믿는다 */
    public static Party load(Long id, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity,
                             List<PartyMember> members, Long hostId, Instant createdAt, PartyStatus status,
                             Integer estimatedFare, Integer estimatedTime, String route, long version,
                             RoomType roomType, Instant departureAt, Instant reminderSentAt, Instant meetingNotifiedAt, int departureReminderCount) {
        return new Party(id, departureLocation, destinationLocation, departure, destination, capacity, members, hostId, createdAt, status,
                estimatedFare, estimatedTime, route, version, roomType, departureAt, reminderSentAt, meetingNotifiedAt, departureReminderCount);
    }

    /** 모집 중(ACTIVE)이고 자리가 남은 방에만 들어온다. 예약 방은 출발 시각부터 닫힌 것으로 본다 */
    public void join(Long memberId, Instant now) {
        if(status != PartyStatus.ACTIVE) {
			throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
		}

        if(isReservation() && !now.isBefore(departureAt)) {
			throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
		}

        if(isFull()) {
			throw new BusinessException(MatchingErrorCode.PARTY_FULL);
		}

        if(isMember(memberId)) {
			throw new BusinessException(MatchingErrorCode.ALREADY_JOINED_PARTY);
		}

        members.add(PartyMember.join(memberId, now));
        version++;
    }

    /**
     *  열린 방이면 도착하기 전까지 언제든 나갈 수 있다. 방장이 나가면 가장 먼저 합류한 멤버가 승계한다.
     *  마지막 멤버가 나가거나, 약속을 확정한 실시간 방에서 혼자 남으면(더 모을 수 없고 혼자서는 출발할 수 없다) 방을 닫는다.
     *  예약 방은 출발 전까지 다시 모을 수 있어 혼자 남아도 열어 둔다 - 출발 시각까지 혼자면 모집 만료가 닫는다
     */
    public void leave(Long memberId) {
        PartyMember member = findMember(memberId);

        ensureOpen();

        // 도착을 누른 사람은 같이 탄 사람이다 - 나가서 정산 대상에서 빠지는 길을 막는다
        if(member.isArrived()) {
            throw new BusinessException(MatchingErrorCode.ARRIVED_MEMBER_CANNOT_LEAVE);
        }

        members.remove(member);
        version++;

        if(members.isEmpty()) {
            hostId = null;
            status = PartyStatus.CANCELED;
            return;
        }

        if(isHost(memberId)) {
			hostId = earliestJoinedMemberId();
		}

        if(status == PartyStatus.COMPLETED && !hasCompanion()) {
			status = PartyStatus.CANCELED;
		}
    }

    // ───────────────────────── 약속 확정 ─────────────────────────

    /**
     *  실시간 방의 방장이 채팅으로 정한 약속 시간과 만남 장소를 확정한다. 장소를 주면 출발지가 그 자리로 바뀐다 -
     *  도착 판정(출발지 100m)이 실제 만남 장소를 기준으로 하게 하기 위해서다. 경로·요금·시간은 미리보기 값이라 다시 구하지 않는다
     */
    public void confirm(Long memberId, MeetingPlan plan, Instant now) {
        ensureHost(memberId);

        ensureOpen();

        if(isReservation()) throw new BusinessException(MatchingErrorCode.NOT_REALTIME_PARTY);

        if(isConfirmed()) throw new BusinessException(MatchingErrorCode.ALREADY_CONFIRMED);

        ensureCompanion();

        Instant meetingAt = plan.departureAt();
        if(meetingAt == null || meetingAt.isBefore(now.plus(MIN_CONFIRM_AHEAD)) || meetingAt.isAfter(now.plus(MAX_CONFIRM_AHEAD))) {
            throw new BusinessException(MatchingErrorCode.INVALID_MEETING_TIME);
        }

        if(plan.hasPlace()) {
            ensureDifferentPlaces(plan.departure(), destinationLocation);
            departureLocation = plan.departure();
            departure = plan.departurePlace();
        }
        departureAt = meetingAt;
        status = PartyStatus.COMPLETED;
        version++;
    }

    // ───────────────────────── 도착·출발 ─────────────────────────

    /**
     *  출발지 도착 - 약속 10분 전부터 출발지 100m 안에서 누른다. 거리는 서비스가 PostGIS 로 재서 nearDeparture 로 넘긴다.
     *
     *  @return 이번에 처음 도착했으면 true, 이미 도착해 있었으면 false(아무것도 바꾸지 않는다)
     */
    public boolean arrive(Long memberId, boolean nearDeparture, Instant now) {
        PartyMember member = findMember(memberId);

        ensureOpen();
        ensureConfirmed();

        if(member.isArrived()) return false;

        if(now.isBefore(departureAt.minus(ARRIVAL_OPENS_BEFORE))) throw new BusinessException(MatchingErrorCode.ARRIVAL_NOT_OPEN);

        if(!nearDeparture) throw new BusinessException(MatchingErrorCode.TOO_FAR_FROM_DEPARTURE);

        member.arrive(now);
        version++;

        return true;
    }

    /** 출발 - 도착한 방장이 약속 시간 뒤에 누르면 방이 끝난다. 도착한 멤버가 탄 사람이고 나머지는 탑승 기록에서 빠질 뿐이다 */
    public void depart(Long memberId, Instant now) {
        PartyMember host = findMember(memberId);
        ensureHost(memberId);

        ensureOpen();
        ensureConfirmed();
        ensureCompanion();

        if(now.isBefore(departureAt)) throw new BusinessException(MatchingErrorCode.MEETING_NOT_STARTED);

        if(!host.isArrived()) throw new BusinessException(MatchingErrorCode.NOT_ARRIVED);

        close(PartyStatus.FINISHED);
    }

    // ───────────────────────── 스케줄러가 닫는 길 ─────────────────────────

    /**
     *  모집 만료 - 실시간 방은 만든 지 15분, 예약 방은 출발 시각에 스케줄러가 부른다. 혼자인 방만 닫는다.
     *  2명 이상이면 방장이 확정할 때까지 그대로 둔다(15분은 혼자면 접는 기준이지 모집 마감이 아니다)
     *
     *  @return 이번에 닫았으면 true
     */
    public boolean expireRecruiting() {
        if(status != PartyStatus.ACTIVE || hasCompanion()) return false;

        close(PartyStatus.CANCELED);
        return true;
    }

    /**
     *  자동 정리 - 아무도 출발을 누르지 않은 채 약속(또는 생성)에서 1일이 지난 방을 스케줄러가 닫는다. 언제 닫을지는 조회 쿼리가 고른다.
     *  약속이 없거나 혼자면 동승이 없었으니 CANCELED, 도착한 멤버가 있으면 그들이 탄 것으로 FINISHED, 없으면 CANCELED
     */
    public AutoCloseResult autoClose() {
        if(status.isClosed()) return AutoCloseResult.skipped(status);

        if(!isConfirmed() || !hasCompanion()) {
            close(PartyStatus.CANCELED);
            return AutoCloseResult.closed(status, List.of());
        }

        List<Long> riderIds = riderIds();
        close(riderIds.isEmpty() ? PartyStatus.CANCELED : PartyStatus.FINISHED);

        return AutoCloseResult.closed(status, riderIds);
    }

    // ───────────────────────── 알림 차례 ─────────────────────────

    /**
     *  약속 시각 알림 - 열린 확정 방에서 약속 시각이 지나면 한 번 처리하고 멤버 전원에게 보낸다.
     *  30분 넘게 늦었거나 혼자인 방은 보내지 않지만 처리 시각은 남겨 다시 고르지 않는다. 알림 기록은 방 상세에 없어 version 은 올리지 않는다
     */
    public PartyNotice markMeetingNotified(Instant now) {
        if(!isOpenAndConfirmed() || meetingNotifiedAt != null || now.isBefore(departureAt)) return PartyNotice.notDue();

        meetingNotifiedAt = now;

        if(now.isAfter(departureAt.plus(MEETING_NOTICE_STALE_AFTER)) || !hasCompanion()) return PartyNotice.silent();

        return PartyNotice.send(memberIds());
    }

    /**
     *  출발 재촉 - 차례 시각이 되면 출발을 누를 수 있는 방장에게 보낸다. 서버가 멈춰 여러 차례가 밀렸으면 지난 차례를 모두 넘기고 한 번만 보낸다.
     *  혼자인 방은 보내지 않고 차례만 넘긴다. 방해 금지 시간은 서비스가 가른다(그때도 차례는 넘긴다)
     */
    public PartyNotice advanceDepartureReminder(Instant now) {
        if(!isOpenAndConfirmed() || departureReminderCount >= DEPARTURE_REMINDER_OFFSETS.size()) return PartyNotice.notDue();

        if(now.isBefore(departureAt.plus(DEPARTURE_REMINDER_OFFSETS.get(departureReminderCount)))) return PartyNotice.notDue();

        do {
            departureReminderCount++;
        } while(departureReminderCount < DEPARTURE_REMINDER_OFFSETS.size()
                && !now.isBefore(departureAt.plus(DEPARTURE_REMINDER_OFFSETS.get(departureReminderCount))));

        if(!hasCompanion()) return PartyNotice.silent();

        return PartyNotice.send(List.of(hostId));
    }

    /** 예약 방 출발 30분 전 알림을 보낸 시각 */
    public void markReminderSent(Instant now) {
        reminderSentAt = now;
    }

    // ───────────────────────── 조회 ─────────────────────────

    /** 약속이 정해진 방 - 예약 방은 만들 때부터, 실시간 방은 방장이 확정한 뒤 */
    public boolean isConfirmed() {
        return isReservation() || departureAt != null;
    }

    public boolean isReservation() {
        return roomType == RoomType.RESERVED;
    }

    public boolean isFull() {
        return members.size() >= capacity.value();
    }

    /** 함께 탈 사람이 있는가 - 확정·출발·알림은 2명 이상일 때만 뜻이 있다 */
    public boolean hasCompanion() {
        return members.size() >= MIN_MEMBERS_TO_DEPART;
    }

    public boolean isHost(Long memberId) {
        return hostId != null && hostId.equals(memberId);
    }

    public boolean isMember(Long memberId) {
        return members.stream().anyMatch(m -> m.getMemberId().equals(memberId));
    }

    public List<Long> memberIds() {
        return members.stream().map(PartyMember::getMemberId).toList();
    }

    /** 탄 사람 - 출발지에 도착한 멤버(합류 순). 정산 대상이다 */
    public List<Long> riderIds() {
        return members.stream().filter(PartyMember::isArrived).map(PartyMember::getMemberId).toList();
    }

    // ───────────────────────── 검사 헬퍼 ─────────────────────────

    private PartyMember findMember(Long memberId) {
        return members.stream()
                .filter(m -> m.getMemberId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER));
    }

    private void ensureHost(Long memberId) {
        findMember(memberId);

        if(!isHost(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_HOST);
    }

    private void ensureOpen() {
        if(status.isClosed()) throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
    }

    private void ensureConfirmed() {
        if(!isConfirmed()) throw new BusinessException(MatchingErrorCode.PARTY_NOT_CONFIRMED);
    }

    private void ensureCompanion() {
        if(!hasCompanion()) throw new BusinessException(MatchingErrorCode.NOT_ENOUGH_MEMBERS);
    }

    private boolean isOpenAndConfirmed() {
        return status.isOpen() && isConfirmed();
    }

    private static void ensureDifferentPlaces(Location departure, Location destination) {
        if(departure.equals(destination)) throw new BusinessException(MatchingErrorCode.SAME_DEPARTURE_DESTINATION);
    }

    private void close(PartyStatus next) {
        status = next;
        version++;
    }

    /** 승계 규칙 - 가장 먼저 합류한 멤버, 합류 시각이 같으면 memberId 가 작은 멤버 */
    private Long earliestJoinedMemberId() {
        return members.stream()
                .min(Comparator.comparing(PartyMember::getJoinedAt).thenComparing(PartyMember::getMemberId))
                .map(PartyMember::getMemberId)
                .orElse(null);
    }

    // ───────────────────────── getter ─────────────────────────

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

    public List<PartyMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    /** 방장. 멤버가 없는 방은 null */
    public Long getHostId() {
        return hostId;
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

    public Instant getMeetingNotifiedAt() {
        return meetingNotifiedAt;
    }

    public int getDepartureReminderCount() {
        return departureReminderCount;
    }
}
