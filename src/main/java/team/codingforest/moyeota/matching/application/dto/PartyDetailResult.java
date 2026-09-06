package team.codingforest.moyeota.matching.application.dto;

import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.PartyMember;
import team.codingforest.moyeota.user.api.MemberSummary;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PartyDetailResult(Long id,
                               Double departureLat, Double departureLng,
                               Double destinationLat, Double destinationLng,
                               String departure, String destination,
                               Integer capacity, Integer currentMembers,
                               Integer departureRadius, Integer destinationRadius,
                               String status, Instant createdAt,
                               List<MemberInfo> members, Integer estimateFare, Integer estimateTime, String route, Long taxiDriverId) {

    public record MemberInfo(UUID publicId, String nickname, String imageUrl, String badgeId, Integer rideCount, Instant joinedAt) {}

    public static PartyDetailResult from(Party party, Map<Long, MemberSummary> summaries, Map<Long, Integer> rideCounts) {
        List<MemberInfo> members = party.getMembers().stream()
                .map(m -> toMemberInfo(m, summaries.get(m.getMemberId()), rideCounts.getOrDefault(m.getMemberId(), 0)))
                .toList();

        return new PartyDetailResult(
                party.getId(), party.getDepartureLocation().latitude(),
                party.getDepartureLocation().longitude(),
                party.getDestinationLocation().latitude(), party.getDestinationLocation().longitude(),
                party.getDeparture(), party.getDestination(), party.getCapacity().value(),
                party.getMembers().size(), party.getDepartureRadius().meters(), party.getDestinationRadius().meters(),
                party.getStatus().name(), party.getCreatedAt(), members,
                party.getEstimatedFare(),
                party.getEstimatedTime(),
                party.getRoute(),
                party.getTaxiDriverId()
        );
    }

    // TODO badgeId 값 설정
    private static MemberInfo toMemberInfo(PartyMember member, MemberSummary summary, int rideCount) {
        // 탈퇴 등으로 유저 요약이 없어도 방 상세 조회 자체는 깨지면 안 된다
        if(summary == null) return new MemberInfo(null, null, null, null, rideCount, member.getJoinedAt());

        return new MemberInfo(summary.publicId(), summary.nickname(), summary.imageUrl(), null, rideCount, member.getJoinedAt());
    }
}
