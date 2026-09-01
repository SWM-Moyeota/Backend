package team.codingforest.moyeota.matching.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;
import lombok.Getter;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 *  애그리거트 루트
  */
// TODO - 예외처리 해야함
@Getter
public class Party {
    private final Long id;
    private final Location departureLocation;
    private final Location destinationLocation;
    private final String departure;
    private final String destination;
    private final Capacity capacity;
    private final Radius departureRadius;
    private final Radius destinationRadius;
    private final List<PartyMember> members;
    private final Instant createdAt;
    private PartyStatus status;
    private final Integer estimatedFare;
    private final Integer estimatedTime;
    private final String route;
    private Long taxiDriverId;
    private Instant matchingStartedAt;

    /**
     * 규칙 생성 -> 아무대서 Party 객체를 생성할 수 없게 하기
     */
    private Party(Long id, Location departureLocation, Location destinationLocation, Radius departureRadius, Radius destinationRadius
            ,String departure, String destination, Capacity capacity, List<PartyMember> members, Instant createdAt, PartyStatus status,
                  Integer estimatedFare, Integer estimatedTime, String route, Long taxiDriverId, Instant matchingStartedAt) {
        this.id = id;
        this.departureLocation = departureLocation;
        this.destinationLocation = destinationLocation;
        this.departure = departure;
        this.destination = destination;
        this.capacity = capacity;
        this.departureRadius = departureRadius;
        this.destinationRadius = destinationRadius;
        this.members = new ArrayList<>(members);
        this.createdAt = createdAt;
        this.status = status;
        this.estimatedFare = estimatedFare;
        this.estimatedTime = estimatedTime;
        this.route = route;
        this.taxiDriverId = taxiDriverId;
        this.matchingStartedAt = matchingStartedAt;
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

        Party party = new Party(null, departureLocation, destinationLocation, departureRadius, destinationRadius, departure, destination, capacity, new ArrayList<>(), createdAt, PartyStatus.ACTIVE,
                estimatedFare, estimatedTime, route, null, null);

        party.members.add(new PartyMember(creatorId, Instant.now()));

        if(party.isFull()) {
            party.status = PartyStatus.COMPLETED;
        }

        return party;
    }

    /**
     *  매칭방 참여
     */
    public void join(Long memberId) {
        if(status != PartyStatus.ACTIVE) throw new BusinessException(MatchingErrorCode.PARTY_CLOSED);
        if(members.size() >= capacity.value()) throw new BusinessException(MatchingErrorCode.PARTY_FULL);
        if(hasMember(memberId)) throw new BusinessException(MatchingErrorCode.ALREADY_JOINED_PARTY);

        members.add(new PartyMember(memberId, Instant.now()));

        if(isFull()) {
            status = PartyStatus.COMPLETED;
        }
    }

    /**
     *  매칭방 나가기
     */
    public void leave(Long memberId) {
        if(!hasMember(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);
        ensureRecruiting();

        PartyMember tmp = null;

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
            return;
        }

        status = PartyStatus.ACTIVE;
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
     *  영속 복원용
     */
    public static Party restore(Long id, Location departureLocation, Location destinationLocation, Radius departureRadius, Radius destinationRadius, String departure, String destination, Capacity capacity, List<PartyMember> members, Instant createdAt, PartyStatus status,
                                    Integer estimatedFare, Integer estimatedTime, String route, Long taxiDriverId, Instant matchingStartedAt) {
        return new Party(id, departureLocation, destinationLocation, departureRadius, destinationRadius, departure, destination, capacity, members, createdAt, status, estimatedFare, estimatedTime, route, taxiDriverId, matchingStartedAt);
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

    private void ensureAssignDriver(Long driverId) {
        if(taxiDriverId == null || !taxiDriverId.equals(driverId)) throw new BusinessException(MatchingErrorCode.NOT_ASSIGNED_DRIVER);
    }

    private void ensureRecruiting() {
        if(!status.isRecruiting()) throw new BusinessException(MatchingErrorCode.PARTY_NOT_RECRUITING);
    }
}
