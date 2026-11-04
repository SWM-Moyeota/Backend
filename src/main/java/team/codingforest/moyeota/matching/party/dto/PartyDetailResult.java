package team.codingforest.moyeota.matching.party.dto;

import team.codingforest.moyeota.matching.party.domain.Party;
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
                                List<MemberInfo> members, Integer estimateFare, Integer estimateTime, String route, Long taxiDriverId,
                                long version, String roomType, Instant departureAt, boolean confirmed) {

    // arrived 는 출발지에 도착했어요를 누른 멤버인지(약속이 정해지기 전에는 늘 false)
    public record MemberInfo(UUID publicId, String nickname, String imageUrl, String badgeId, Integer rideCount, boolean host, boolean arrived) {

        // TODO badgeId 값 설정
        public static MemberInfo of(MemberSummary summary, int rideCount, boolean host, boolean arrived) {
            if(summary == null) return new MemberInfo(null, null, null, null, rideCount, host, arrived);

            return new MemberInfo(summary.publicId(), summary.nickname(), summary.imageUrl(), null, rideCount, host, arrived);
        }
    }

    public static PartyDetailResult from(Party party, Map<Long, MemberSummary> summaries, Map<Long, Integer> rideCounts) {
        List<MemberInfo> members = party.getMembers().stream()
                .map(m -> MemberInfo.of(summaries.get(m.getMemberId()), rideCounts.getOrDefault(m.getMemberId(), 0),
                        party.isHost(m.getMemberId()), m.isArrived()))
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
                party.getTaxiDriverId(),
                party.getVersion(),
                party.getRoomType().name(),
                party.getDepartureAt(),
                party.isConfirmed()
        );
    }
}
