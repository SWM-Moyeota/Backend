package team.codingforest.moyeota.matching.record;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse.RideRecordItem;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse.RideRecordMember;
import team.codingforest.moyeota.matching.party.PartyJpaTest;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideRecordServiceTest {
    private static final Long 나 = 1L;
    private static final Long 동승자 = 2L;
    private static final Long 셋째 = 3L;
    private static final Long 남 = 4L;
    private static final Long 기사 = 9L;
    private static final Instant 기준 = Instant.parse("2026-10-01T10:00:00.123456Z");

    private PartyJpaTest parties;
    private FakeRideRecords rideRecords;
    private StubUserAccess users;
    private RideRecordService service;

    @BeforeEach
    void setUp() {
        parties = new PartyJpaTest();
        rideRecords = new FakeRideRecords(parties);
        users = new StubUserAccess();
        service = new RideRecordService(rideRecords, users);
    }

    @Test
    void 기록이_없으면_빈_목록이고_다음_페이지가_없으며_멤버와_사용자는_조회하지_않는다() {
        RideRecordResponse response = service.findMyRides(나, null, 20);

        assertThat(response.rides()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
        assertThat(rideRecords.memberLookups).isZero();
        assertThat(users.summaryLookups).isZero();
    }

    @Test
    void 하차_최신순으로_size_만큼_주고_nextCursor_로_끝까지_한_번씩_이어진다() {
        Long 첫째 = ride(기준.plusSeconds(10), 나);
        Long 둘째 = ride(기준.plusSeconds(20), 나);
        Long 셋째방 = ride(기준.plusSeconds(30), 나);
        Long 넷째 = ride(기준.plusSeconds(40), 나);
        Long 다섯째 = ride(기준.plusSeconds(50), 나);

        RideRecordResponse 첫페이지 = service.findMyRides(나, null, 2);
        RideRecordResponse 둘째페이지 = service.findMyRides(나, 첫페이지.nextCursor(), 2);
        RideRecordResponse 셋째페이지 = service.findMyRides(나, 둘째페이지.nextCursor(), 2);

        assertThat(partyIds(첫페이지)).containsExactly(다섯째, 넷째);
        assertThat(첫페이지.hasNext()).isTrue();
        assertThat(partyIds(둘째페이지)).containsExactly(셋째방, 둘째);
        assertThat(둘째페이지.hasNext()).isTrue();
        assertThat(partyIds(셋째페이지)).containsExactly(첫째);
        assertThat(셋째페이지.hasNext()).isFalse();
        assertThat(셋째페이지.nextCursor()).isNull();
    }

    @Test
    void 기록_수가_size_의_배수면_마지막_페이지에서_다음_페이지가_없다() {
        ride(기준.plusSeconds(10), 나);
        ride(기준.plusSeconds(20), 나);
        ride(기준.plusSeconds(30), 나);
        ride(기준.plusSeconds(40), 나);

        RideRecordResponse 첫페이지 = service.findMyRides(나, null, 2);
        RideRecordResponse 둘째페이지 = service.findMyRides(나, 첫페이지.nextCursor(), 2);

        assertThat(첫페이지.hasNext()).isTrue();
        assertThat(첫페이지.nextCursor()).isNotNull();
        assertThat(둘째페이지.rides()).hasSize(2);
        assertThat(둘째페이지.hasNext()).isFalse();
        assertThat(둘째페이지.nextCursor()).isNull();
    }

    @Test
    void 하차_시각이_같으면_partyId_큰_순으로_페이지가_빠짐없이_이어진다() {
        Instant 같은시각 = 기준.plusSeconds(20);
        Long 이전 = ride(기준.plusSeconds(10), 나);
        Long 동률1 = ride(같은시각, 나);
        Long 동률2 = ride(같은시각, 나);
        Long 동률3 = ride(같은시각, 나);

        List<Long> seen = new ArrayList<>();
        Long cursor = null;
        do {
            RideRecordResponse page = service.findMyRides(나, cursor, 1);
            seen.addAll(partyIds(page));
            cursor = page.nextCursor();
        } while(cursor != null && seen.size() < 10);

        assertThat(seen).containsExactly(동률3, 동률2, 동률1, 이전);
    }

    @Test
    void 먼저_만든_방이_나중에_끝나도_하차_순서대로_나온다() {
        Long 늦게끝남 = ride(기준.plusSeconds(30), 나);
        Long 일찍끝남 = ride(기준.plusSeconds(10), 나);
        Long 중간 = ride(기준.plusSeconds(20), 나);

        assertThat(partyIds(service.findMyRides(나, null, 20))).containsExactly(늦게끝남, 중간, 일찍끝남);
    }

    @Test
    void nextCursor_는_페이지_마지막_기록의_partyId_다() {
        ride(기준.plusSeconds(10), 나);
        Long 둘째 = ride(기준.plusSeconds(20), 나);
        ride(기준.plusSeconds(30), 나);

        assertThat(service.findMyRides(나, null, 2).nextCursor()).isEqualTo(둘째);
    }

    @Test
    void 방_id_순서와_하차_순서가_달라도_커서로_하차_순서대로_이어진다() {
        Long 늦게끝남 = ride(기준.plusSeconds(30), 나);
        Long 일찍끝남 = ride(기준.plusSeconds(10), 나);
        Long 중간 = ride(기준.plusSeconds(20), 나);

        RideRecordResponse 첫페이지 = service.findMyRides(나, null, 1);
        RideRecordResponse 둘째페이지 = service.findMyRides(나, 첫페이지.nextCursor(), 1);
        RideRecordResponse 셋째페이지 = service.findMyRides(나, 둘째페이지.nextCursor(), 1);

        assertThat(partyIds(첫페이지)).containsExactly(늦게끝남);
        assertThat(partyIds(둘째페이지)).containsExactly(중간);
        assertThat(partyIds(셋째페이지)).containsExactly(일찍끝남);
        assertThat(셋째페이지.hasNext()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 51, 1000})
    void size_가_1_미만이거나_50_을_넘으면_INVALID_PAGE_SIZE(int size) {
        assertThatThrownBy(() -> service.findMyRides(나, null, size))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_PAGE_SIZE);
    }

    @Test
    void size_는_1과_50_을_그대로_받는다() {
        for(int i = 0; i < 51; i++) {
            ride(기준.plusSeconds(i), 나);
        }

        assertThat(service.findMyRides(나, null, 1).rides()).hasSize(1);
        assertThat(service.findMyRides(나, null, 50).rides()).hasSize(50);
        assertThat(service.findMyRides(나, null, 50).hasNext()).isTrue();
    }

    @Test
    void 없는_기록의_커서는_INVALID_CURSOR_이고_조회하지_않는다() {
        ride(기준, 나);

        assertThatThrownBy(() -> service.findMyRides(나, 999L, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_CURSOR);
        assertThat(rideRecords.memberLookups).isZero();
        assertThat(users.summaryLookups).isZero();
    }

    @Test
    void 아직_하차_전인_기록의_커서는_INVALID_CURSOR() {
        Long 운행중 = savedParty(members(나));
        rideRecords.save(RideRecord.pickUp(운행중, 기사, "강남역", "판교역"));

        assertThatThrownBy(() -> service.findMyRides(나, 운행중, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_CURSOR);
    }

    @Test
    void 남의_기록을_커서로_보내도_그_하차_시각_이전의_내_기록만_나온다() {
        Long 내이전 = ride(기준.plusSeconds(10), 나);
        Long 남의기록 = ride(기준.plusSeconds(20), 동승자, 남);
        ride(기준.plusSeconds(30), 나);

        assertThat(partyIds(service.findMyRides(나, 남의기록, 20))).containsExactly(내이전);
    }

    @Test
    void 멤버는_나를_포함해_합류_순으로_담기고_id_순서와_달라도_합류_순을_따른다() {
        MemberSummary 동승자정보 = users.등록(동승자, "지민");
        MemberSummary 내정보 = users.등록(나, "성윤");
        MemberSummary 셋째정보 = users.등록(셋째, "지우");
        Long partyId = savedParty(List.of(PartyMember.load(셋째, 기준.plusSeconds(2)), PartyMember.load(나, 기준.plusSeconds(1)),
                PartyMember.load(동승자, 기준)));
        dropOff(partyId, 기준.plusSeconds(600), 9600);

        RideRecordItem ride = service.findMyRides(나, null, 20).rides().getFirst();

        assertThat(ride.members()).extracting(RideRecordMember::publicId)
                .containsExactly(동승자정보.publicId(), 내정보.publicId(), 셋째정보.publicId());
        assertThat(ride.members()).extracting(RideRecordMember::nickname).containsExactly("지민", "성윤", "지우");
        assertThat(ride.currentMembers()).isEqualTo(3);
    }

    @Test
    void 기록_항목은_운행_정보와_실제_요금과_1인_요금을_담는다() {
        Long partyId = savedParty(members(나, 동승자, 셋째));
        Instant 탑승 = 기준.plusSeconds(60);
        Instant 하차 = 기준.plusSeconds(1800);
        rideRecords.save(RideRecord.load(partyId, 기사, "부산대학교 정문", "서면역 1번 출구", 탑승, 하차, 10000));

        RideRecordItem ride = service.findMyRides(나, null, 20).rides().getFirst();

        assertThat(ride.partyId()).isEqualTo(partyId);
        assertThat(ride.departure()).isEqualTo("부산대학교 정문");
        assertThat(ride.destination()).isEqualTo("서면역 1번 출구");
        assertThat(ride.pickupTime()).isEqualTo(탑승);
        assertThat(ride.dropoffTime()).isEqualTo(하차);
        assertThat(ride.totalFare()).isEqualTo(10000);
        assertThat(ride.farePerPerson()).as("1/N 정수 나눗셈 - 나머지 버림").isEqualTo(3333);
        assertThat(ride.currentMembers()).isEqualTo(3);
    }

    @Test
    void 혼자_탄_기록은_1인_요금이_실제_요금과_같다() {
        dropOff(savedParty(members(나)), 기준, 12345);

        RideRecordItem ride = service.findMyRides(나, null, 20).rides().getFirst();

        assertThat(ride.farePerPerson()).isEqualTo(12345);
        assertThat(ride.currentMembers()).isEqualTo(1);
    }

    @Test
    void 탑승_시각_없이_만들어진_기록은_pickupTime_이_null_이다() {
        Long partyId = savedParty(members(나));
        rideRecords.save(RideRecord.load(partyId, 기사, "강남역", "판교역", null, 기준, 12000));

        assertThat(service.findMyRides(나, null, 20).rides().getFirst().pickupTime()).isNull();
    }

    @Test
    void 기사_없이_끝난_합승_기록은_탑승_시각과_요금과_1인_요금이_null_이다() {
        Long partyId = savedParty(members(나, 동승자));
        rideRecords.save(RideRecord.load(partyId, null, "강남역", "판교역", null, 기준, null));

        var ride = service.findMyRides(나, null, 20).rides().getFirst();

        assertThat(ride.partyId()).isEqualTo(partyId);
        assertThat(ride.pickupTime()).isNull();
        assertThat(ride.dropoffTime()).isEqualTo(기준);
        assertThat(ride.totalFare()).isNull();
        assertThat(ride.farePerPerson()).isNull();
        assertThat(ride.currentMembers()).isEqualTo(2);
    }

    @Test
    void 탈퇴한_동승자는_publicId_를_유지하고_탈퇴한_사용자와_기본_이미지로_보인다() {
        users.등록(나, "성윤");
        UUID 탈퇴자공개ID = users.등록(동승자, "지민").publicId();
        users.탈퇴(동승자);
        dropOff(savedParty(members(나, 동승자)), 기준, 12000);

        RideRecordMember 탈퇴자 = service.findMyRides(나, null, 20).rides().getFirst().members().get(1);

        assertThat(탈퇴자.publicId()).isEqualTo(탈퇴자공개ID);
        assertThat(탈퇴자.nickname()).isEqualTo(StubUserAccess.탈퇴자_닉네임);
        assertThat(탈퇴자.imageUrl()).isEqualTo(StubUserAccess.기본_이미지_URL);
    }

    @Test
    void 사용자_정보가_없는_멤버는_세_값이_모두_null_이고_인원에는_센다() {
        users.등록(나, "성윤");
        dropOff(savedParty(members(나, 동승자)), 기준, 12000);

        RideRecordItem ride = service.findMyRides(나, null, 20).rides().getFirst();
        RideRecordMember 없는멤버 = ride.members().get(1);

        assertThat(없는멤버.publicId()).isNull();
        assertThat(없는멤버.nickname()).isNull();
        assertThat(없는멤버.imageUrl()).isNull();
        assertThat(ride.currentMembers()).isEqualTo(2);
        assertThat(ride.farePerPerson()).isEqualTo(6000);
    }

    @Test
    void 운행_중인_기록과_내가_없는_방의_기록은_나오지_않는다() {
        Long 내기록 = ride(기준.plusSeconds(10), 나, 동승자);
        ride(기준.plusSeconds(20), 동승자, 남);
        rideRecords.save(RideRecord.pickUp(savedParty(members(나)), 기사, "강남역", "판교역"));

        assertThat(partyIds(service.findMyRides(나, null, 20))).containsExactly(내기록);
    }

    @Test
    void 페이지마다_멤버와_사용자는_한_번씩만_조회하고_같은_사람은_한_번만_묻는다() {
        ride(기준.plusSeconds(10), 나, 동승자);
        ride(기준.plusSeconds(20), 나, 동승자, 셋째);
        ride(기준.plusSeconds(30), 나, 셋째);

        service.findMyRides(나, null, 20);

        assertThat(rideRecords.memberLookups).isEqualTo(1);
        assertThat(users.summaryLookups).isEqualTo(1);
        assertThat(users.lastRequested).containsExactlyInAnyOrder(나, 동승자, 셋째);
    }

    private List<Long> partyIds(RideRecordResponse response) {
        return response.rides().stream().map(RideRecordItem::partyId).toList();
    }

    private Long ride(Instant dropoffTime, Long... memberIds) {
        return dropOff(savedParty(members(memberIds)), dropoffTime, 12000);
    }

    private Long dropOff(Long partyId, Instant dropoffTime, int fare) {
        rideRecords.save(RideRecord.load(partyId, 기사, "강남역", "판교역", dropoffTime.minusSeconds(600), dropoffTime, fare));
        return partyId;
    }

    private List<PartyMember> members(Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for(int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], 기준.plusSeconds(i)));
        }
        return members;
    }

    private Long savedParty(List<PartyMember> members) {
        return parties.save(Party.load(null, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), new Radius(100), new Radius(100),
                "강남역", "판교역", new Capacity(3), members, 기준, PartyStatus.FINISHED, 12000, 25, "_p~iF~ps|U_ulLnnqC", 기사, 기준, 기준, 0L)).getId();
    }

    static class StubUserAccess implements UserAccess {
        static final String 탈퇴자_닉네임 = "탈퇴한 사용자";
        static final String 기본_이미지_URL = "https://cdn.example.com/profile/presets/default.png";

        private final Map<Long, MemberSummary> summaries = new HashMap<>();
        int summaryLookups = 0;
        List<Long> lastRequested = List.of();

        MemberSummary 등록(Long userId, String nickname) {
            MemberSummary summary = new MemberSummary(UUID.randomUUID(), nickname, "https://cdn.example.com/profile/" + userId + ".png", null);
            summaries.put(userId, summary);
            return summary;
        }

        void 탈퇴(Long userId) {
            summaries.put(userId, new MemberSummary(summaries.get(userId).publicId(), 탈퇴자_닉네임, 기본_이미지_URL, null));
        }

        @Override
        public Map<Long, String> findFcmTokens(List<Long> userIds) {
            return Map.of();
        }

        @Override
        public Map<Long, MemberSummary> findMemberSummaries(List<Long> userIds) {
            summaryLookups++;
            lastRequested = List.copyOf(userIds);
            Map<Long, MemberSummary> result = new HashMap<>();
            for(Long id : userIds) {
                if(summaries.containsKey(id)) result.put(id, summaries.get(id));
            }
            return result;
        }

        @Override
        public Optional<String> findNickname(Long userId) {
            return Optional.ofNullable(summaries.get(userId)).map(MemberSummary::nickname);
        }
    }
}
