package team.codingforest.moyeota.matching.record.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.matching.faresplit.domain.RideFare;
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
                                  @Schema(description = "출발지 이름(탑승 당시 방의 출발지)") String departure,
                                  @Schema(description = "도착지 이름(탑승 당시 방의 도착지)") String destination,
                                  @Schema(description = "탑승 시각. 서버가 채우지 않아 null 이다(출시 앱 호환용으로 키만 남김)") Instant pickupTime,
                                  @Schema(description = "하차 시각 - 출발을 누르거나 방장이 동승 종료하거나 서버가 자동 정리한 시각") Instant dropoffTime,
                                  @Schema(description = "실제 택시비(원) - 이 방의 택시비 정산에 받는 사람이 넣은 금액. 정산이 없거나(취소 포함) 내가 정산의 받는 사람·보내는 사람이 아니면 null") Integer totalFare,
                                  @Schema(description = "내 부담 금액(원). 보내는 사람은 1인당 보낼 금액(10원 단위 올림), 받는 사람은 택시비에서 다른 사람들이 보낼 금액을 뺀 금액. totalFare 가 null 이면 null") Integer farePerPerson,
                                  @Schema(description = "함께 탄 인원(나 포함). 노쇼는 빠진다") int currentMembers,
                                  @Schema(description = "함께 탄 멤버(나 포함, 합류 순). 노쇼는 빠진다. 본인 판별은 publicId 와 토큰 sub 비교") List<RideRecordMember> members) {

        static RideRecordItem of(RideRecord rideRecord, List<Long> memberIds, Map<Long, MemberSummary> summaries, RideFare fare, Long memberId) {
            List<RideRecordMember> members = memberIds.stream()
                    .map(id -> RideRecordMember.of(summaries.get(id)))
                    .toList();

            // 출시 앱이 읽는 키라 값이 없어도 null 로 남긴다 - totalFare·farePerPerson 은 함께 채우거나 함께 비운다(둘 다 null 이면 앱이 요금 줄을 숨긴다)
            Integer totalFare = fare == null ? null : fare.totalFare();
            Integer farePerPerson = fare == null ? null : fare.amountOf(memberId);

            return new RideRecordItem(rideRecord.getMatchId(), rideRecord.getDeparturePlace(), rideRecord.getDestinationPlace(),
                    null, rideRecord.getDropoffTime(), totalFare, farePerPerson, members.size(), members);
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

    /**
     *  @param fares    방별 정산 금액. 정산이 없거나 내가 정산에 없는 방은 빠져 있다
     *  @param memberId 기록을 보는 사람 - fares 에서 내 몫을 고른다
     */
    public static RideRecordResponse of(List<RideRecord> rideRecords, Map<Long, List<Long>> memberIds, Map<Long, MemberSummary> summaries,
                                         Map<Long, RideFare> fares, Long memberId, Long nextCursor, boolean hasNext) {
        List<RideRecordItem> rides = rideRecords.stream()
                .map(h -> RideRecordItem.of(h, memberIds.getOrDefault(h.getMatchId(), List.of()), summaries, fares.get(h.getMatchId()), memberId))
                .toList();

        return new RideRecordResponse(rides, nextCursor, hasNext);
    }
}
