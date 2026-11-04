package team.codingforest.moyeota.matching.record.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.user.api.MemberSummary;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RideRecordResponse(@Schema(description = "탑승 기록. 하차 시각 최신순(같으면 partyId 큰 순)") List<RideRecordItem> rides,
                                  @Schema(description = "다음 페이지 커서 - 이 페이지 마지막 기록의 partyId. 그대로 다음 요청의 cursor 로 보낸다. 마지막 페이지면 null") Long nextCursor,
                                  @Schema(description = "다음 페이지가 있는지") boolean hasNext) {

    public record RideRecordItem(@Schema(description = "방 ID") Long partyId,
                                  @Schema(description = "출발지 이름(운행 당시 방의 출발지)") String departure,
                                  @Schema(description = "도착지 이름(운행 당시 방의 도착지)") String destination,
                                  @Schema(description = "탑승 시각. 서버가 채우지 않아 null 이다(출시 앱 호환용으로 키만 남김)") Instant pickupTime,
                                  @Schema(description = "하차 시각 - 출발을 누르거나 방장이 합승 종료하거나 서버가 자동 정리한 시각") Instant dropoffTime,
                                  @Schema(description = "실제 요금(원). 서버가 받지 않아 null 이다(출시 앱 호환용으로 키만 남김)") Integer totalFare,
                                  @Schema(description = "1인 부담 요금(원) - totalFare / currentMembers 정수 나눗셈(나머지 버림). 1/N 균등 분할 기준. totalFare 가 null 이면 null") Integer farePerPerson,
                                  @Schema(description = "함께 탄 인원(나 포함). 노쇼는 빠진다") int currentMembers,
                                  @Schema(description = "함께 탄 멤버(나 포함, 합류 순). 노쇼는 빠진다. 본인 판별은 publicId 와 토큰 sub 비교") List<RideRecordMember> members) {

        static RideRecordItem of(RideRecord rideRecord, List<Long> memberIds, Map<Long, MemberSummary> summaries) {
            List<RideRecordMember> members = memberIds.stream()
                    .map(id -> RideRecordMember.of(summaries.get(id)))
                    .toList();

            return new RideRecordItem(rideRecord.getMatchId(), rideRecord.getDeparturePlace(), rideRecord.getDestinationPlace(),
                    rideRecord.getPickupTime(), rideRecord.getDropoffTime(), rideRecord.getTotalFare(),
                    farePerPerson(rideRecord.getTotalFare(), members.size()), members.size(), members);
        }

        private static Integer farePerPerson(Integer totalFare, int currentMembers) {
            if(totalFare == null || currentMembers == 0) return null;

            return totalFare / currentMembers;
        }
    }

    public record RideRecordMember(@Schema(description = "공개 ID. 탈퇴한 사용자도 그대로 유지된다. 사용자 정보가 없으면 null") UUID publicId,
                                    @Schema(description = "닉네임. 탈퇴한 사용자는 \"탈퇴한 사용자\". 사용자 정보가 없으면 null") String nickname,
                                    @Schema(description = "프로필 이미지 URL. 탈퇴한 사용자는 기본 이미지 URL. 사용자 정보가 없으면 null") String imageUrl) {

        static RideRecordMember of(MemberSummary summary) {
            if(summary == null) return new RideRecordMember(null, null, null);

            return new RideRecordMember(summary.publicId(), summary.nickname(), summary.imageUrl());
        }
    }

    public static RideRecordResponse of(List<RideRecord> rideRecords, Map<Long, List<Long>> memberIds,
                                         Map<Long, MemberSummary> summaries, Long nextCursor, boolean hasNext) {
        List<RideRecordItem> rides = rideRecords.stream()
                .map(h -> RideRecordItem.of(h, memberIds.getOrDefault(h.getMatchId(), List.of()), summaries))
                .toList();

        return new RideRecordResponse(rides, nextCursor, hasNext);
    }
}
