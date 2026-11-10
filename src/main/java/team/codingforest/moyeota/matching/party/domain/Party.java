package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 동승 방 애그리거트 루트. 상태 전이는 PartyStatus 주석의 표를 따른다.
 */
public class Party {
	public static final Duration MIN_CONFIRM_AHEAD = Duration.ofMinutes(5);
	public static final Duration MAX_CONFIRM_AHEAD = Duration.ofHours(1);
	public static final Duration MAX_RESERVATION_AHEAD = Duration.ofDays(7);
	public static final double REALTIME_ARRIVAL_RADIUS_METERS = 100;
	public static final double RESERVATION_ARRIVAL_RADIUS_METERS = 400;
	public static final Duration ARRIVAL_OPENS_BEFORE = Duration.ofMinutes(10);
	public static final int MIN_MEMBERS_TO_DEPART = 2;
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
	private Instant departureAt;

	private Party(Long id, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity,
	              List<PartyMember> members, Long hostId, Instant createdAt, PartyStatus status,
	              Integer estimatedFare, Integer estimatedTime, String route, long version,
	              RoomType roomType, Instant departureAt) {
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
	}


	public static Party create(Long creatorId, Location departureLocation, Location destinationLocation, String departure, String destination,
	                           Capacity capacity, Instant now, Integer estimatedFare, Integer estimatedTime, String route) {
		validateDifferentPlaces(departureLocation, destinationLocation);

		if (estimatedFare == null || estimatedFare < 0 || estimatedTime == null || estimatedTime < 0 || route == null) {
			throw new BusinessException(MatchingErrorCode.INVALID_ROUTE_ESTIMATE);
		}

		return open(creatorId, departureLocation, destinationLocation, departure, destination, capacity, now,
				estimatedFare, estimatedTime, route, RoomType.REALTIME, null);
	}

	public static Party reserve(Long creatorId, Location departureLocation, Location destinationLocation, String departure, String destination,
	                            Capacity capacity, Instant now, Instant departureAt) {
		validateDifferentPlaces(departureLocation, destinationLocation);

		if (!departureAt.isAfter(now) || departureAt.isAfter(now.plus(MAX_RESERVATION_AHEAD))) {
			throw new BusinessException(MatchingErrorCode.INVALID_DEPARTURE_TIME);
		}

		return open(creatorId, departureLocation, destinationLocation, departure, destination, capacity, now,
				null, null, null, RoomType.RESERVED, departureAt);
	}

	private static Party open(Long creatorId, Location departureLocation, Location destinationLocation, String departure, String destination,
	                          Capacity capacity, Instant now, Integer estimatedFare, Integer estimatedTime, String route,
	                          RoomType roomType, Instant departureAt) {
		Party party = new Party(null, departureLocation, destinationLocation, departure, destination, capacity, List.of(), creatorId, now,
				PartyStatus.ACTIVE, estimatedFare, estimatedTime, route, 0L, roomType, departureAt);

		party.members.add(PartyMember.join(creatorId, now));

		return party;
	}

	public static Party load(Long id, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity,
	                         List<PartyMember> members, Long hostId, Instant createdAt, PartyStatus status,
	                         Integer estimatedFare, Integer estimatedTime, String route, long version,
	                         RoomType roomType, Instant departureAt) {
		return new Party(id, departureLocation, destinationLocation, departure, destination, capacity, members, hostId, createdAt, status,
				estimatedFare, estimatedTime, route, version, roomType, departureAt);
	}

	/**
	 * 모집 중(ACTIVE)이고 자리가 남은 방에만 들어온다. 예약 방은 출발 시각부터 닫힌 것으로 본다
	 */
	public void join(Long memberId, Instant now) {
		if (status != PartyStatus.ACTIVE) {
			throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
		}

		if (isReservation() && !now.isBefore(departureAt)) {
			throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
		}

		if (isFull()) {
			throw new BusinessException(MatchingErrorCode.PARTY_FULL);
		}

		if (isMember(memberId)) {
			throw new BusinessException(MatchingErrorCode.ALREADY_JOINED_PARTY);
		}

		members.add(PartyMember.join(memberId, now));
		version++;
	}

	/**
	 * 열린 방이면 도착하기 전까지 언제든 나갈 수 있다. 방장이 나가면 가장 먼저 합류한 멤버가 승계한다.
	 * 마지막 멤버가 나가거나, 약속을 확정한 실시간 방에서 혼자 남으면(더 모을 수 없고 혼자서는 출발할 수 없다) 방을 닫는다.
	 * 예약 방은 출발 전까지 다시 모을 수 있어 혼자 남아도 열어 둔다 - 출발 시각까지 혼자면 모집 만료가 닫는다
	 */
	public void leave(Long memberId) {
		PartyMember member = findMember(memberId);

		validateOpen();

		// 도착을 누른 사람은 같이 탄 사람으로 정산 대상에서 빠지면 안됨
		if (member.isArrived()) {
			throw new BusinessException(MatchingErrorCode.ARRIVED_MEMBER_CANNOT_LEAVE);
		}

		members.remove(member);
		version++;

		if (members.isEmpty()) {
			hostId = null;
			status = PartyStatus.CANCELED;
			return;
		}

		if (isHost(memberId)) {
			hostId = earliestJoinedMemberId();
		}

		if (status == PartyStatus.COMPLETED && !hasCompanion()) {
			status = PartyStatus.CANCELED;
		}
	}

	/**
	 * 실시간 방의 방장이 채팅으로 정한 약속 시간과 만남 장소를 확정한다. 장소를 바꾸지 않았어도 받은 장소로 출발지를 고정한다 -
	 * 도착 판정이 실제 만남 장소를 기준으로 하게 하기 위해서다. 경로·요금·시간은 미리보기 값이라 다시 구하지 않는다
	 */
	public void confirm(Long memberId, MeetingPlan plan, Instant now) {
		validateHost(memberId);

		validateOpen();

		if (isReservation()) {
			throw new BusinessException(MatchingErrorCode.NOT_REALTIME_PARTY);
		}

		if (isConfirmed()) {
			throw new BusinessException(MatchingErrorCode.ALREADY_CONFIRMED);
		}

		validateCompanion();

		Instant meetingAt = plan.departureAt();

		if (meetingAt == null || meetingAt.isBefore(now.plus(MIN_CONFIRM_AHEAD)) || meetingAt.isAfter(now.plus(MAX_CONFIRM_AHEAD))) {
			throw new BusinessException(MatchingErrorCode.INVALID_MEETING_TIME);
		}

		validateDifferentPlaces(plan.departure(), destinationLocation);

		departureLocation = plan.departure();
		departure = plan.departurePlace();
		departureAt = meetingAt;
		status = PartyStatus.COMPLETED;
		version++;
	}

	/**
	 * 출발지 도착 - 약속 10분 전부터 출발지 반경(arrivalRadiusMeters) 안에서 누른다. 이미 눌렀으면 false, 처음 도착하면 true
	 */
	public boolean arrive(Long memberId, boolean nearDeparture, Instant now) {
		PartyMember member = findMember(memberId);

		validateOpen();
		validateConfirmed();

		if (member.isArrived()) {
			return false;
		}

		if (now.isBefore(departureAt.minus(ARRIVAL_OPENS_BEFORE))) {
			throw new BusinessException(MatchingErrorCode.ARRIVAL_NOT_OPEN);
		}

		if (!nearDeparture) {
			throw new BusinessException(MatchingErrorCode.TOO_FAR_FROM_DEPARTURE);
		}

		member.arrive(now);
		version++;

		return true;
	}

	/**
	 * 출발 - 도착한 방장이 약속 시간 뒤에 누르면 방이 끝난다.
	 */
	public void depart(Long memberId, Instant now) {
		PartyMember host = findMember(memberId);
		validateHost(memberId);

		validateOpen();
		validateConfirmed();
		validateCompanion();

		if (now.isBefore(departureAt)) {
			throw new BusinessException(MatchingErrorCode.MEETING_NOT_STARTED);
		}

		if (!host.isArrived()) {
			throw new BusinessException(MatchingErrorCode.NOT_ARRIVED);
		}

		close(PartyStatus.FINISHED);
	}

	/**
	 * 출발 전 알림·약속 시각 알림을 받을 사람 - 열린 확정 방에 함께 탈 사람이 있으면 멤버 전원, 아니면 빈 목록
	 */
	public List<Long> noticeRecipients() {
		if (!isOpenAndConfirmed() || !hasCompanion()) {
			return List.of();
		}

		return memberIds();
	}

	/**
	 * 출발 재촉을 받을 사람 - 출발 버튼을 누르는 방장. 알림 대상이 아닌 방이면 빈 목록
	 */
	public List<Long> departureReminderRecipients() {
		if (!isOpenAndConfirmed() || !hasCompanion()) {
			return List.of();
		}

		return List.of(hostId);
	}

	/**
	 * 자동 정리 - 아무도 출발을 누르지 않은 채 약속(또는 생성)에서 1일이 지난 방을 스케줄러가 닫는다. 언제 닫을지는 조회 쿼리가 고른다.
	 * 약속이 없거나 혼자면 동승이 없었으니 CANCELED, 도착한 멤버가 있으면 그들이 탄 것으로 FINISHED, 없으면 CANCELED
	 */
	public AutoCloseResult autoClose() {
		if (status.isClosed()) {
			return AutoCloseResult.skipped(status);
		}

		if (!isConfirmed() || !hasCompanion()) {
			close(PartyStatus.CANCELED);
			return AutoCloseResult.closed(status, List.of());
		}

		List<Long> riderIds = riderIds();
		close(riderIds.isEmpty() ? PartyStatus.CANCELED : PartyStatus.FINISHED);

		return AutoCloseResult.closed(status, riderIds);
	}

	/**
	 * 출발지 도착으로 인정하는 반경(m). 예약 방은 출발지가 장소 검색 결과의 중심점(큰 역이면 역 한가운데)이고
	 * 확정으로 만남 장소를 옮길 수 없어 실제로 만나는 출구까지 들어오도록 넓게 잡는다
	 */
	public double arrivalRadiusMeters() {
		return isReservation() ? RESERVATION_ARRIVAL_RADIUS_METERS : REALTIME_ARRIVAL_RADIUS_METERS;
	}

	public boolean isConfirmed() {
		return isReservation() || departureAt != null;
	}

	public boolean isReservation() {
		return roomType == RoomType.RESERVED;
	}

	public boolean isFull() {
		return members.size() >= capacity.value();
	}

	/**
	 * 함께 탈 사람이 있는가 - 확정·출발·알림은 2명 이상일 때만 가능
	 */
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

	/**
	 * 탄 사람 - 출발지에 도착한 멤버(합류 순). 정산 대상이다
	 */
	public List<Long> riderIds() {
		return members.stream().filter(PartyMember::isArrived).map(PartyMember::getMemberId).toList();
	}

	private PartyMember findMember(Long memberId) {
		return members.stream()
				.filter(m -> m.getMemberId().equals(memberId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER));
	}

	private void validateHost(Long memberId) {
		findMember(memberId);

		if (!isHost(memberId)) {
			throw new BusinessException(MatchingErrorCode.NOT_PARTY_HOST);
		}
	}

	private void validateOpen() {
		if (status.isClosed()) {
			throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
		}
	}

	private void validateConfirmed() {
		if (!isConfirmed()) {
			throw new BusinessException(MatchingErrorCode.PARTY_NOT_CONFIRMED);
		}
	}

	private void validateCompanion() {
		if (!hasCompanion()) {
			throw new BusinessException(MatchingErrorCode.NOT_ENOUGH_MEMBERS);
		}
	}

	private boolean isOpenAndConfirmed() {
		return status.isOpen() && isConfirmed();
	}

	private static void validateDifferentPlaces(Location departure, Location destination) {
		if (departure.equals(destination)) {
			throw new BusinessException(MatchingErrorCode.SAME_DEPARTURE_DESTINATION);
		}
	}

	private void close(PartyStatus next) {
		status = next;
		version++;
	}

	/**
	 * 가장 먼저 합류한 멤버, 합류 시각이 같으면 memberId 가 작은 멤버
	 */
	private Long earliestJoinedMemberId() {
		return members.stream()
				.min(Comparator.comparing(PartyMember::getJoinedAt).thenComparing(PartyMember::getMemberId))
				.map(PartyMember::getMemberId)
				.orElse(null);
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

	public List<PartyMember> getMembers() {
		return Collections.unmodifiableList(members);
	}

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
}
