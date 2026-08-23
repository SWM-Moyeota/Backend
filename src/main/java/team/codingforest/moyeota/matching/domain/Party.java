package team.codingforest.moyeota.matching.domain;

import lombok.Getter;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 *  애그리거트 루트
  */
// TODO - 예외처리 해야함
@Getter
public class Party {
    private final Long id;
    private Long hostId;
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

    /**
     * 규칙 생성 -> 아무대서 Party 객체를 생성할 수 없게 하기
     */
    private Party(Long id, Long hostId, Location departureLocation, Location destinationLocation, Radius departureRadius, Radius destinationRadius
            ,String departure, String destination, Capacity capacity, List<PartyMember> members, Instant createdAt, PartyStatus status,
                  Integer estimatedFare, Integer estimatedTime, String route, Long taxiDriverId) {
        this.id = id;
        this.hostId = hostId;
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
    }

    /**
     *  매칭방 생성
     */
    public static Party open(Long hostId, Location departureLocation, Location destinationLocation, String departure, String destination, Capacity capacity,
                             Instant createdAt, Radius departureRadius, Radius destinationRadius, Integer estimatedFare, Integer estimatedTime, String route) {
        if(departureLocation.equals(destinationLocation)) throw new IllegalArgumentException("출발지와 도착지가 같습니다.");

        if(estimatedFare == null || estimatedFare < 0) throw new IllegalArgumentException("예상 요금이 올바르지 않습니다.");

        if(estimatedTime == null || estimatedTime < 0) throw new IllegalArgumentException("예상 시간이 올바르지 않습니다.");

        if(route == null) throw new IllegalArgumentException("경로가 존재하지 않습니다.");

        Party party = new Party(null, hostId, departureLocation, destinationLocation, departureRadius, destinationRadius, departure, destination, capacity, new ArrayList<>(), createdAt, PartyStatus.ACTIVE,
                estimatedFare, estimatedTime, route, null);

        party.members.add(new PartyMember(hostId, Instant.now()));

        return party;
    }

    /**
     *  매칭방 참여
     */
    public void join(Long memberId) {
        if(status != PartyStatus.ACTIVE) throw new IllegalArgumentException("마감된 방임");
        if(members.size() >= capacity.value()) throw new IllegalArgumentException("이미 꽉찬 방임");
        if(hasMember(memberId)) throw new IllegalArgumentException("이미 참여한 방임");

        members.add(new PartyMember(memberId, Instant.now()));

        if(isFull()) {
            status = PartyStatus.COMPLETED;
        }
    }

    /**
     *  매칭방 나가기
     */
    public void leave(Long memberId) {
        if(!hasMember(memberId)) throw new IllegalArgumentException("해당 방에 참여X");
        ensureNotClosed();
        ensureNotMatching();

        // 방장 혼자 남은 경우 방 폭파
        if(members.size() == 1) {
            cancel(memberId);
            return;
        }

        PartyMember tmp = null;

        // 방장인 경우 찾기
        for(PartyMember m : members) {
            if(m.getMemberId().equals(memberId)) {
                tmp = m;
                break;
            }
        }

        members.remove(tmp);

        if(Objects.equals(this.hostId, memberId)) {
            hostId = assignNewHost();
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
    public static Party restore(Long id, Long hostId, Location departureLocation, Location destinationLocation, Radius departureRadius, Radius destinationRadius, String departure, String destination, Capacity capacity, List<PartyMember> members, Instant createdAt, PartyStatus status,
                                    Integer estimatedFare, Integer estimatedTime, String route, Long taxiDriverId) {
        return new Party(id, hostId, departureLocation, destinationLocation, departureRadius, destinationRadius, departure, destination, capacity, members, createdAt, status, estimatedFare, estimatedTime, route, taxiDriverId);
    }

    /**
     *  방장이 방을 나간 경우 가장 먼저 들어온 인원으로 방장 주기
     */
    public Long assignNewHost() {
        return members.stream()
                .min(Comparator.comparing(PartyMember::getJoinedAt))
                .orElseThrow(() -> new IllegalArgumentException("방장을 새롭게 할당할 수 없습니다."))
                .getMemberId();
    }

    /**
     *  방장 혼자 남은 경우 방 폭파
     */
    public void cancel(Long memberId) {
        ensureHost(memberId);
        ensureNotClosed();
        ensureNotMatching();

        status = PartyStatus.CANCELED;
    }

    /**
     *  신규 참여 멤버는 NOT_READY로 시작
     */
    public void ready(Long memberId) {
        ensureNotClosed();
        ensureNotMatching();

        findMember(memberId).ready();
    }

    public void cancelReady(Long memberId) {
        ensureNotClosed();
        ensureNotMatching();

        findMember(memberId).cancelReady();
    }

    public void startMatching(Long memberId) {
        ensureHost(memberId);
        ensureNotMatching();
        ensureNotClosed();

        if(!isAllReady()) throw new IllegalArgumentException("전원 준비 완료 상태가 아닙니다.");

        status = PartyStatus.MATCHING;
    }

    /**
     *  전원 준비 완료했는지 여부 확인
     */
    public boolean isAllReady() {
        for(PartyMember m : members) {
            if(!m.isReady()) return false;
        }

        return true;
    }

    private PartyMember findMember(Long memberId) {
        for(PartyMember m : members) {
            if(m.getMemberId().equals(memberId)) return m;
        }

        throw new IllegalArgumentException("해당 방에 참여하지 않았습니다.");
    }

    public void assignDriver(Long driverId) {
        if(status != PartyStatus.MATCHING) throw new IllegalArgumentException("매칭 중인 방이 아닙니다.");

        if(taxiDriverId != null) throw new IllegalArgumentException("이미 기사가 배정된 방입니다.");

        this.taxiDriverId = driverId;
    }

    private void ensureHost(Long memberId) {
        if(!hostId.equals(memberId)) throw new IllegalArgumentException("방장이 아닙니다.");
    }

    private void ensureNotClosed() {
        if(status.isClosed()) throw new IllegalArgumentException("이미 취소되거나 종료된 방입니다.");
    }

    private void ensureNotMatching() {
        if(status == PartyStatus.MATCHING)  throw new IllegalArgumentException("이미 매칭이 시작된 방입니다.");
    }
}
