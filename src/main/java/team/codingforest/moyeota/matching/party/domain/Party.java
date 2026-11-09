package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 *  애그리거트 루트
  */
public class Party {
	private static final Duration MIN_CONFIRM_AHEAD = Duration.ofMinutes(5);   // CONFIRM_CLOCK_SKEW 삭제
	private static final Duration MAX_CONFIRM_AHEAD = Duration.ofHours(1);

    private static final Duration MAX_RESERVATION_AHEAD = Duration.ofDays(7);
    public static final double ARRIVAL_RADIUS_METERS = 100;

    public static final Duration ARRIVAL_OPENS_BEFORE = Duration.ofMinutes(10);

    private static final int MIN_MEMBERS_TO_DEPART = 2;
    // 약속 시각 알림은 이만큼 넘게 늦으면(서버가 멈췄다 다시 돈 경우) "약속 시간이에요"가 맞지 않아 보내지 않는다
    static final Duration MEETING_NOTICE_STALE_AFTER = Duration.ofMinutes(30);
    /**
     *  출발 재촉 시각 - 약속 시각에서 이만큼 지난 때. n 번째 재촉(0부터)은 departureAt + DEPARTURE_REMINDER_OFFSETS[n] 이고 이 수만큼만 보낸다.
     *  재촉 스케줄러의 대상 조회(PartyJpaRepository.findDepartureReminderTargetIds)가 차례마다 기준 시각을 하나씩 받으므로 개수를 바꾸면 쿼리도 함께 바꾼다
     */
    public static final List<Duration> DEPARTURE_REMINDER_OFFSETS = List.of(
            Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(4));

    private final Long id;
    private final Location departureLocation;
    private final Location destinationLocation;
    private final String departure;
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
    // 예약 방은 만들 때 정한 출발 시각, 실시간 방은 방장이 확정한 약속 시간(확정 전에는 null). 바꾸는 길은 confirm 하나뿐이다
    private Instant departureAt;
    private Instant reminderSentAt;
    // 약속 시각 알림을 처리한 시각(늦어서 보내지 않은 경우도 처리로 남긴다). 약속이 정해진 방에서만 채워진다
    private Instant meetingNotifiedAt;
    // 처리한 출발 재촉 차례 수(0..DEPARTURE_REMINDER_OFFSETS.size()). 방해 금지 시간·도착한 사람 없음으로 보내지 않은 차례도 센다
    private int departureReminderCount;

    /**
     * 규칙 생성 -> 아무대서 Party 객체를 생성할 수 없게 하기
     */
    private Party(Long id, Location departureLocation, Location destinationLocation
            ,String departure, String destination, Capacity capacity, List<PartyMember> members, Long hostId, Instant createdAt, PartyStatus status,
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

    /**
     *  매칭방 생성
     */
    public static Party create(Long creatorId, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity,
                               Instant createdAt, Integer estimatedFare, Integer estimatedTime, String route) {
        if(departureLocation.equals(destinationLocation)) {
			throw new BusinessException(MatchingErrorCode.SAME_DEPARTURE_DESTINATION);
		}

        if(estimatedFare == null || estimatedFare < 0) {
			throw new BusinessException(MatchingErrorCode.INVALID_ROUTE_ESTIMATE);
		}

        if(estimatedTime == null || estimatedTime < 0) {
			throw new BusinessException(MatchingErrorCode.INVALID_ROUTE_ESTIMATE);
		}

        if(route == null) {
			throw new BusinessException(MatchingErrorCode.INVALID_ROUTE_ESTIMATE);
		}

        Party party = new Party(null, departureLocation, destinationLocation, departure, destination, capacity, new ArrayList<>(), creatorId, createdAt, PartyStatus.ACTIVE,
                estimatedFare, estimatedTime, route, 0L, RoomType.REALTIME, null, null, null, 0);

        party.members.add(new PartyMember(creatorId, Instant.now()));

        return party;
    }

	/**
	 * 	매칭방 예약
	 */
    public static Party reserve(Long creatorId, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity,
                                Instant createdAt, Instant departureAt) {
        if(departureLocation.equals(destinationLocation)) {
			throw new BusinessException(MatchingErrorCode.SAME_DEPARTURE_DESTINATION);
		}

        if(!departureAt.isAfter(createdAt) || departureAt.isAfter(createdAt.plus(MAX_RESERVATION_AHEAD))) {
			throw new BusinessException(MatchingErrorCode.INVALID_DEPARTURE_TIME);
		}

        Party party = new Party(null, departureLocation, destinationLocation, departure, destination, capacity, new ArrayList<>(), creatorId, createdAt, PartyStatus.ACTIVE,
                null, null, null, 0L, RoomType.RESERVED, departureAt, null, null, 0);

        party.members.add(new PartyMember(creatorId, Instant.now()));

        return party;
    }

    /**
     *  매칭방 참여
     */
    public void join(Long memberId) {
		if(isFull()) throw new BusinessException(MatchingErrorCode.PARTY_FULL);

        if(status != PartyStatus.ACTIVE) {
			throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
		}

        if(isReservation() && !Instant.now().isBefore(departureAt)) {
			throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
		}

        if(members.size() >= capacity.value()) {
			throw new BusinessException(MatchingErrorCode.PARTY_FULL);
		}

        if(hasMember(memberId)) {
			throw new BusinessException(MatchingErrorCode.ALREADY_JOINED_PARTY);
		}

        members.add(new PartyMember(memberId, Instant.now()));
        version++;
    }

    /**
     *  매칭방 나가기
     */
    public void leave(Long memberId) {
        if(!hasMember(memberId)) {
			throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);
		}

        validateRecruiting();

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

        // 마지막 사용자도 나간 경우 방 없애기
        if(members.isEmpty()) {
            cancel();
            hostId = null;
            return;
        }

		// 방장이 나간 경우 가장 일찍 들어온 사람이 방장이 된다.
        if(memberId.equals(hostId)) {
            hostId = earliestJoinedMemberId(members);
        }
    }

    /**
     *  실시간 방의 약속 확정으로 채팅을 통해 의견을 조율
     *  확정하면 모집도 끝난다: ACTIVE 면 COMPLETED 로 넘긴다.
     */
    public void confirm(Long memberId, Instant departureAt, Instant now) {
        if(!isHost(memberId)) {
			throw new BusinessException(MatchingErrorCode.NOT_PARTY_HOST);
		}

		// 예약 매칭방인지 확인 + 이미 확정된 방인지
		if(isConfirmed()) {
			throw new BusinessException(MatchingErrorCode.ALREADY_CONFIRMED);
		}

        validateRecruiting();

        if(members.size() < MIN_MEMBERS_TO_DEPART) {
			throw new BusinessException(MatchingErrorCode.NOT_ENOUGH_MEMBERS);
		}

        if(departureAt == null || departureAt.isBefore(now.plus(MIN_CONFIRM_AHEAD)) || departureAt.isAfter(now.plus(MAX_CONFIRM_AHEAD))) {
			throw new BusinessException(MatchingErrorCode.INVALID_MEETING_TIME);
		}

        this.departureAt = departureAt;

        if(status == PartyStatus.ACTIVE) {
			completeRecruiting();
		}

        version++;
    }

    /**
	 *  매칭방이 확정인지 확인
     */
    public boolean isConfirmed() {
        return isReservation() || departureAt != null;
    }

    /**
     *  출발지 도착 확인 만약 이미 도착한 경우 false 처음 호출해서 도착한 경우 true를 리턴받는다.
     */
    public boolean arrive(Long memberId, boolean nearDeparture, Instant now) {
        PartyMember member = findMember(memberId);

		// 아닌 경우 예외 처리
        ensureAttendanceOpen();

        if(member.isArrived()) {
			return false;
		}

        if(now.isBefore(departureAt.minus(ARRIVAL_OPENS_BEFORE))) {
			throw new BusinessException(MatchingErrorCode.ARRIVAL_NOT_OPEN);
		}

        if(!nearDeparture) {
			throw new BusinessException(MatchingErrorCode.TOO_FAR_FROM_DEPARTURE);
		}

        member.arrive(now);
        version++;

        return true;
    }

    /**
     *  출발 - 방장이 멤버가 약속 시간 뒤에 누르면 방이 바로 끝난다(FINISHED).
	 *  arrive 메서드를 호출하지 않은 인원들은 노쇼로 기록 -> 노쇼한 유저 id값 기록
     */
    public List<Long> depart(Long memberId, Instant now) {
		if(!isHost(memberId)) {
			throw new BusinessException(MatchingErrorCode.NOT_PARTY_HOST);
		}

        PartyMember member = findMember(memberId);

        ensureAttendanceOpen();

        if(members.size() < MIN_MEMBERS_TO_DEPART) {
			throw new BusinessException(MatchingErrorCode.NOT_ENOUGH_MEMBERS);
		}

        if(now.isBefore(departureAt)) {
			throw new BusinessException(MatchingErrorCode.MEETING_NOT_STARTED);
		}

        if(!member.isArrived()) {
			throw new BusinessException(MatchingErrorCode.NOT_ARRIVED);
		}

        List<Long> noShowMemberIds = markNoShowsExceptArrived(now);

        status = PartyStatus.FINISHED;
        version++;

        return noShowMemberIds;
    }

	/**
	 *  도착하지 않은 멤버를 모두 노쇼로 기록
	 */
	private List<Long> markNoShowsExceptArrived(Instant now) {
		List<Long> noShowMemberIds = new ArrayList<>();

		for(PartyMember m : members) {
			if(m.isArrived()) continue;

			m.markNoShow(now);
			noShowMemberIds.add(m.getMemberId());
		}

		return noShowMemberIds;
	}

	/**
	 *  노쇼 이의를 낼 수 있는 멤버인지 확인
	 */
	public void ensureNoShow(Long memberId) {
		if(!findMember(memberId).isNoShow()) {
			throw new BusinessException(MatchingErrorCode.NOT_NO_SHOW);
		}
	}

    /**
     *  자동 정리 - 실시간 방은 만든 지 1일, 예약 방은 출발 시각에서 1일이 지나도 열려 있으면(ACTIVE·COMPLETED) 정리 스케줄러가 닫는다.
     *  언제 닫을지는 스케줄러 조회가 가르고, 여기서는 어떻게 닫을지를 정한다.
     *  약속이 정해진 방은 출발(depart)과 같은 기준으로 노쇼를 가른다
     *   - 도착한 멤버가 있으면 FINISHED, 도착하지 않은 멤버는 노쇼다. 도착한 멤버가 실제로 탄 사람이다
     *   - 아무도 도착하지 않았으면 동승이 없었으니 CANCELED, 멤버 전원이 노쇼다. 도착 버튼을 누르지 않은 것도 본인 몫이고 이의는 운영자가 노쇼를 무효로 돌려 받는다
     *   - 함께 탈 사람이 들어오지 않은 예약 방(멤버 1명)은 모집 정리와 같이 노쇼 없이 CANCELED - 출발도 할 수 없던 방이다
     *  약속을 정하지 않은 실시간 방은 노쇼를 가릴 약속이 없다 - COMPLETED 는 이전 자동 종료처럼 FINISHED(구버전 앱), ACTIVE 는 CANCELED.
     *  조회와 행 잠금 사이에 출발·나가기로 이미 닫힌 방은 PARTY_CLOSED 로 거절한다(스케줄러는 건너뛴다)
     */
    public AutoCloseResult autoClose(Instant now) {
        if(!status.isRecruiting()) {
			throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
		}

        // 만든 지 1일이 다 돼 확정한 실시간 방은 약속이 그 뒤일 수 있다 - 만나기도 전에 전원을 노쇼로 찍지 않게 약속 시간이 지난 다음 정리에서 닫는다
        if(isConfirmed() && now.isBefore(departureAt)) {
			throw new BusinessException(MatchingErrorCode.MEETING_NOT_STARTED);
		}

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
     *  운영자가 이의를 받아들여 멤버의 노쇼를 무효로 돌린다. 무효가 된 노쇼는 탑승 기록·탑승 횟수에 다시 잡힌다.
     *  방이 끝난 뒤의 일이고 방 상세·SSE 에 보이는 값이 바뀌지 않아 version 은 올리지 않는다
     *  @return 이번에 무효로 돌렸으면 true, 이미 무효였으면 false
     */
    public boolean voidNoShow(Long memberId, Instant now) {
        PartyMember member = findMember(memberId);

        if(member.getNoShowAt() == null) throw new BusinessException(MatchingErrorCode.NOT_NO_SHOW);

        return member.voidNoShow(now);
    }

    /**
     *  약속 시각 알림 - 약속이 정해진 열린 방(ACTIVE·COMPLETED)에서 약속 시각이 지나면 한 번 처리한다. 받는 사람은 멤버 전원이다.
     *  방해 금지 시간은 따르지 않는다(약속이 밤일 수 있다). 처리하면 알림을 보내지 않는 경우에도 처리한 시각을 남겨 다시 고르지 않는다
     *   - 약속 시각에서 30분 넘게 지났으면(서버가 멈췄다 다시 돈 경우) 보내지 않는다 - 늦게 온 "약속 시간이에요"는 쓸모가 없다
     *   - 함께 탈 사람이 들어오지 않은 방(멤버 1명 - 예약 방만 그럴 수 있다)은 모집 정리에서 취소될 방이라 보내지 않는다
     *  알림 기록은 방 상세·SSE 에 보이지 않아 version 은 올리지 않는다
     */
    public PartyNotice markMeetingNotified(Instant now) {
        if(!status.isRecruiting() || !isConfirmed()) return PartyNotice.notDue();

        if(meetingNotifiedAt != null || now.isBefore(departureAt)) return PartyNotice.notDue();

        meetingNotifiedAt = now;

        if(now.isAfter(departureAt.plus(MEETING_NOTICE_STALE_AFTER))) return PartyNotice.silent();

        if(members.size() < MIN_MEMBERS_TO_DEPART) return PartyNotice.silent();

        return PartyNotice.send(members.stream().map(PartyMember::getMemberId).toList());
    }

    /**
     *  다음 출발 재촉 시각 - departureAt + DEPARTURE_REMINDER_OFFSETS[처리한 차례 수].
     *  열린 방(ACTIVE·COMPLETED)이 아니거나(출발·자동 정리로 닫히면 재촉도 끝난다) 약속이 없거나 차례를 다 썼으면 없다
     */
    public Optional<Instant> nextDepartureReminderDue() {
        if(!status.isRecruiting() || !isConfirmed()) return Optional.empty();

        if(departureReminderCount >= DEPARTURE_REMINDER_OFFSETS.size()) return Optional.empty();

        return Optional.of(departureAt.plus(DEPARTURE_REMINDER_OFFSETS.get(departureReminderCount)));
    }

    /**
     *  출발 재촉 차례를 처리한다. 출발 버튼은 도착한 멤버만 누를 수 있어 받는 사람은 도착한 멤버다.
     *  서버가 멈춰 여러 차례가 한꺼번에 밀렸으면 지난 차례를 모두 넘기고 한 번만 보낸다.
     *  도착한 멤버가 없거나 함께 탈 사람이 없는 방(멤버 1명)이면 보내지 않고 차례만 넘긴다. 방해 금지 시간은 서비스가 가른다(그때도 차례는 넘긴다)
     */
    public PartyNotice advanceDepartureReminder(Instant now) {
        Optional<Instant> due = nextDepartureReminderDue();

        if(due.isEmpty() || now.isBefore(due.get())) return PartyNotice.notDue();

        do {
            departureReminderCount++;
        } while(departureReminderCount < DEPARTURE_REMINDER_OFFSETS.size()
                && !now.isBefore(departureAt.plus(DEPARTURE_REMINDER_OFFSETS.get(departureReminderCount))));

        if(members.size() < MIN_MEMBERS_TO_DEPART) return PartyNotice.silent();

        return PartyNotice.send(members.stream().filter(PartyMember::isArrived).map(PartyMember::getMemberId).toList());
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
     *  탑승자 - 출발지에 도착했고 노쇼가 아닌 멤버(합류 순). 도착 없이 끝난 방(약속을 정하지 않은 구버전 종료)은 탑승자가 없다
     */
    public List<Long> riderIds() {
        return members.stream()
                .filter(m -> m.isArrived() && !m.isNoShow())
                .map(PartyMember::getMemberId)
                .toList();
    }

    /**
     *  이미 꽉찬 방인지 확인
     */
    public boolean isFull() {
        return members.size() == capacity.value();
    }

    /**
     *  영속 복원용
     */
    public static Party load(Long id, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity, List<PartyMember> members, Long hostId, Instant createdAt, PartyStatus status,
                             Integer estimatedFare, Integer estimatedTime, String route, long version,
                             RoomType roomType, Instant departureAt, Instant reminderSentAt, Instant meetingNotifiedAt, int departureReminderCount) {
        return new Party(id, departureLocation, destinationLocation, departure, destination, capacity, members, restoreHostId(members, hostId), createdAt, status, estimatedFare, estimatedTime, route, version, roomType, departureAt, reminderSentAt,
                meetingNotifiedAt, departureReminderCount);
    }

    /**
     *  약속 시각 알림 컬럼(meeting_notified_at, departure_reminder_count)이 생기기 전 모양. 알림은 처리한 적 없는 것으로 복원한다
     */
    public static Party load(Long id, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity, List<PartyMember> members, Long hostId, Instant createdAt, PartyStatus status,
                             Integer estimatedFare, Integer estimatedTime, String route, long version,
                             RoomType roomType, Instant departureAt, Instant reminderSentAt) {
        return load(id, departureLocation, destinationLocation, departure, destination, capacity, members, hostId, createdAt, status, estimatedFare, estimatedTime, route, version, roomType, departureAt, reminderSentAt,
                null, 0);
    }

    /**
     *  host_id 컬럼이 생기기 전 모양(실시간 방). 방장은 승계 규칙(= V25 백필 규칙)으로 멤버 목록에서 정한다
     */
    public static Party load(Long id, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity, List<PartyMember> members, Instant createdAt, PartyStatus status,
                                    Integer estimatedFare, Integer estimatedTime, String route, long version) {
        return load(id, departureLocation, destinationLocation, departure, destination, capacity, members, null, createdAt, status, estimatedFare, estimatedTime, route, version, RoomType.REALTIME, null, null);
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
        validateRecruiting();

        status = PartyStatus.CANCELED;
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
    public void finishUnconfirmed(Long memberId) {
        if(!hasMember(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);

        // 방장 검사보다 먼저 - 확정된 방에선 방장이 아니어도 출발 버튼으로 끝낼 수 있으니 그 길을 알려 준다
        if(isConfirmed()) throw new BusinessException(MatchingErrorCode.USE_DEPART_FOR_CONFIRMED_PARTY);

        if(!isHost(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_HOST);

        if(status != PartyStatus.COMPLETED) throw new BusinessException(MatchingErrorCode.PARTY_NOT_COMPLETED);

        status = PartyStatus.FINISHED;
        version++;
    }

    private void validateRecruiting() {
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
