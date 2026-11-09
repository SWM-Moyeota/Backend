package team.codingforest.moyeota.matching.record.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.faresplit.FareSplitService;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.matching.party.PartyService;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingPlan;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;
import team.codingforest.moyeota.matching.record.RideRecordService;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.record.domain.RideRecords;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse.RideRecordItem;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.profiles.active=dev")
class RideRecordQueryPostgresTest {
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);

    @Autowired Parties parties;
    @Autowired PartyService partyService;
    @Autowired RideRecords rideRecords;
    @Autowired RideRecordService rideRecordService;
    @Autowired FareSplitService fareSplitService;
    @Autowired JdbcTemplate jdbc;

    private final Long 나 = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
    private final Long 동승자 = 나 + 1;
    private final Long 남 = 나 + 2;
    private final Long 넷째 = 나 + 3;
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(id -> {
            jdbc.update("DELETE FROM fare_split_transfer WHERE match_id = ?", id);
            jdbc.update("DELETE FROM fare_split WHERE match_id = ?", id);
            jdbc.update("DELETE FROM ride_record WHERE match_id = ?", id);
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    @Test
    void 방장이_출발하면_기록이_남고_동승자의_탑승_기록으로_조회된다() {
        Party party = Party.create(나, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(2), Instant.now(),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
        party.join(동승자, Instant.now());
        Long partyId = track(parties.save(party).getId());
        // 약속을 확정한 뒤 약속 시각을 방금 지난 시각으로 돌려 바로 도착·출발을 누른다
        partyService.confirm(partyId, 나, new MeetingPlan(Instant.now().plus(Duration.ofMinutes(10)), null, null));
        jdbc.update("UPDATE match_room SET departure_at = now() - interval '30 seconds' WHERE id = ?", partyId);
        partyService.arrive(partyId, 나, DEPARTURE.latitude(), DEPARTURE.longitude());
        partyService.arrive(partyId, 동승자, DEPARTURE.latitude(), DEPARTURE.longitude());

        partyService.depart(partyId, 나);

        RideRecord rideRecord = rideRecords.findById(partyId).orElseThrow();
        assertThat(rideRecord.getDeparturePlace()).isEqualTo("강남역");
        assertThat(rideRecord.getDestinationPlace()).isEqualTo("판교역");
        assertThat(rideRecord.getDropoffTime()).isNotNull();

        RideRecordResponse response = rideRecordService.findMyRides(동승자, null, 20);
        RideRecordItem ride = response.rides().getFirst();
        assertThat(response.rides()).hasSize(1);
        assertThat(ride.partyId()).isEqualTo(partyId);
        assertThat(ride.pickupTime()).isNull();
        assertThat(ride.dropoffTime()).isEqualTo(rideRecord.getDropoffTime());
        assertThat(ride.totalFare()).isNull();
        assertThat(ride.farePerPerson()).isNull();
        assertThat(ride.currentMembers()).isEqualTo(2);
    }

    @Test
    void 하차_시각이_같은_기록도_커서로_빠짐없이_이어지고_남의_방과_하차_전인_방은_빠진다() {
        Instant 같은시각 = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Long 이전 = droppedOff(같은시각.minusSeconds(60), 나);
        Long 동률1 = droppedOff(같은시각, 나, 동승자);
        Long 동률2 = droppedOff(같은시각, 동승자, 나);
        Long 이후 = droppedOff(같은시각.plusSeconds(60), 나);
        droppedOff(같은시각, 동승자, 남);
        rideRecords.save(RideRecord.load(savedParty(members(같은시각, 나)), "강남역", "판교역", null));

        List<Long> seen = new ArrayList<>();
        Long cursor = null;
        do {
            RideRecordResponse page = rideRecordService.findMyRides(나, cursor, 2);
            page.rides().forEach(r -> seen.add(r.partyId()));
            cursor = page.nextCursor();
        } while(cursor != null && seen.size() < 10);

        assertThat(seen).containsExactly(이후, 동률2, 동률1, 이전);
    }

    @Test
    void 방별_멤버는_합류_순으로_모이고_id_순서와_달라도_합류_순을_따른다() {
        Instant 기준 = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Long partyId = savedParty(List.of(PartyMember.load(남, 기준.plusSeconds(2), null), PartyMember.load(나, 기준.plusSeconds(1), null),
                PartyMember.load(동승자, 기준, null)));

        assertThat(rideRecords.findMemberIds(List.of(partyId)).get(partyId)).containsExactly(동승자, 나, 남);
    }

    @Test
    void 택시비_정산이_있는_방은_받는_사람과_보내는_사람에게_각자_몫이_채워지고_정산에_없는_멤버와_정산이_없는_방은_null_이다() {
        Instant 기준 = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant 합류 = 기준.minusSeconds(3600);
        Instant 도착 = 합류.plusSeconds(600);
        // 남은 도착을 누르지 않아 탑승자가 아니다 - 기록에도 정산에도 없다
        Long 정산방 = droppedOff(기준, List.of(PartyMember.load(동승자, 합류, 도착), PartyMember.load(나, 합류.plusSeconds(1), 도착),
                PartyMember.load(넷째, 합류.plusSeconds(2), 도착), PartyMember.load(남, 합류.plusSeconds(3), null)));
        Long 정산없는방 = droppedOff(기준.minusSeconds(60), 나, 동승자);
        fareSplitService.create(정산방, 나, 18_700, Bank.KB, "3333-01-2345678");

        assertThat(rideOf(나, 정산방).totalFare()).isEqualTo(18_700);
        assertThat(rideOf(나, 정산방).farePerPerson()).as("받는 사람 몫 = 18,700 - 6,240 × 2").isEqualTo(6_220);
        assertThat(rideOf(동승자, 정산방).totalFare()).isEqualTo(18_700);
        assertThat(rideOf(동승자, 정산방).farePerPerson()).as("보내는 사람 몫 = 1인당 금액").isEqualTo(6_240);
        assertThat(rideOf(넷째, 정산방).farePerPerson()).isEqualTo(6_240);
        assertThat(rideRecordService.findMyRides(남, null, 20).rides()).as("도착하지 않은 멤버에게는 기록이 없다").isEmpty();
        assertThat(rideOf(나, 정산없는방).totalFare()).isNull();
        assertThat(rideOf(나, 정산없는방).farePerPerson()).isNull();

        // 닫힌 정산은 계좌번호가 지워져도 금액은 남는다
        jdbc.update("UPDATE fare_split SET status = 'COMPLETED', account_number = NULL, closed_at = now() WHERE match_id = ?", 정산방);

        assertThat(rideOf(나, 정산방).totalFare()).isEqualTo(18_700);
        assertThat(rideOf(나, 정산방).farePerPerson()).isEqualTo(6_220);
        assertThat(rideOf(동승자, 정산방).farePerPerson()).isEqualTo(6_240);
    }

    @Test
    void 택시비_정산을_취소하면_탑승_기록의_요금이_다시_null_이다() {
        Instant 기준 = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant 합류 = 기준.minusSeconds(3600);
        Long 정산방 = droppedOff(기준, List.of(PartyMember.load(나, 합류, 합류.plusSeconds(600)),
                PartyMember.load(동승자, 합류.plusSeconds(1), 합류.plusSeconds(600))));
        fareSplitService.create(정산방, 나, 10_001, Bank.KAKAO, "3333-01-2345678");
        assertThat(rideOf(동승자, 정산방).farePerPerson()).isEqualTo(5_010);

        fareSplitService.cancel(정산방, 나);

        assertThat(rideOf(나, 정산방).totalFare()).isNull();
        assertThat(rideOf(나, 정산방).farePerPerson()).isNull();
        assertThat(rideOf(동승자, 정산방).totalFare()).isNull();
        assertThat(rideOf(동승자, 정산방).farePerPerson()).isNull();
    }

    @Test
    void 없는_방을_가리키는_기록은_저장되지_않는다() {
        Long 없는방 = track(Long.MAX_VALUE);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ride_record (match_id, departure_place, destination_place, created_at) VALUES (?, ?, ?, now())
                """, 없는방, "강남역", "판교역"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private RideRecordItem rideOf(Long memberId, Long partyId) {
        return rideRecordService.findMyRides(memberId, null, 50).rides().stream()
                .filter(r -> r.partyId().equals(partyId))
                .findFirst().orElseThrow();
    }

    private Long droppedOff(Instant dropoffTime, Long... memberIds) {
        return droppedOff(dropoffTime, members(dropoffTime.minusSeconds(3600), memberIds));
    }

    private Long droppedOff(Instant dropoffTime, List<PartyMember> members) {
        Long partyId = savedParty(members);
        rideRecords.save(RideRecord.load(partyId, "강남역", "판교역", dropoffTime));
        return partyId;
    }

    private List<PartyMember> members(Instant joinedFrom, Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for(int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], joinedFrom.plusSeconds(i), null));
        }
        return members;
    }

    /** 끝난(FINISHED) 실시간 방. 방장은 가장 먼저 합류한 멤버다 */
    private Long savedParty(List<PartyMember> members) {
        Instant now = Instant.now();
        Long hostId = members.stream()
                .min(Comparator.comparing(PartyMember::getJoinedAt).thenComparing(PartyMember::getMemberId))
                .map(PartyMember::getMemberId).orElse(null);
        Long partyId = track(parties.save(Party.load(null, DEPARTURE, DESTINATION, "강남역", "판교역",
                new Capacity(3), members, hostId, now, PartyStatus.FINISHED, 12000, 25, "_p~iF~ps|U_ulLnnqC", 0L,
                RoomType.REALTIME, null)).getId());
        // 합류 시각(created_at)은 auditing 이 저장 시각으로 채운다 - 픽스처의 합류 시각을 행에 그대로 옮긴다
        members.forEach(member -> jdbc.update("UPDATE user_match_room SET created_at = ? WHERE match_id = ? AND user_id = ?",
                OffsetDateTime.ofInstant(member.getJoinedAt(), ZoneOffset.UTC), partyId, member.getMemberId()));
        return partyId;
    }

    private Long track(Long partyId) {
        created.add(partyId);
        return partyId;
    }
}
