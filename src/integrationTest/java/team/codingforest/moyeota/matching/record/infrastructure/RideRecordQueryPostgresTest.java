package team.codingforest.moyeota.matching.record.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.record.RideRecordService;
import team.codingforest.moyeota.matching.record.domain.RideRecords;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse.RideRecordItem;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.Radius;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.profiles.active=dev")
class RideRecordQueryPostgresTest {
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);
    private static final Long 기사 = 9L;

    @Autowired Parties parties;
    @Autowired PartyAccess partyAccess;
    @Autowired RideRecords rideRecords;
    @Autowired RideRecordService rideRecordService;
    @Autowired JdbcTemplate jdbc;

    private final Long 나 = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
    private final Long 동승자 = 나 + 1;
    private final Long 남 = 나 + 2;
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(id -> {
            jdbc.update("DELETE FROM ride_record WHERE match_id = ?", id);
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    @Test
    void 기사가_태우고_요금을_입력하면_기록이_남고_동승자의_탑승_기록으로_조회된다() {
        Party party = Party.open(나, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(2), Instant.now(),
                new Radius(100), new Radius(100), 12000, 25, "_p~iF~ps|U_ulLnnqC");
        party.join(동승자);
        party.startMatching(Instant.now());
        Long partyId = track(parties.save(party).getId());

        partyAccess.assignDriver(partyId, 기사);
        partyAccess.startRide(partyId, 기사);
        partyAccess.completeRide(partyId, 기사, 15001);

        RideRecord rideRecord = rideRecords.findById(partyId).orElseThrow();
        assertThat(rideRecord.getTaxiDriverId()).isEqualTo(기사);
        assertThat(rideRecord.getDeparturePlace()).isEqualTo("강남역");
        assertThat(rideRecord.getDestinationPlace()).isEqualTo("판교역");
        assertThat(rideRecord.getPickupTime()).isNotNull();
        assertThat(rideRecord.getDropoffTime()).isAfterOrEqualTo(rideRecord.getPickupTime());
        assertThat(rideRecord.getTotalFare()).isEqualTo(15001);

        RideRecordResponse response = rideRecordService.findMyRides(동승자, null, 20);
        RideRecordItem ride = response.rides().getFirst();
        assertThat(response.rides()).hasSize(1);
        assertThat(ride.partyId()).isEqualTo(partyId);
        assertThat(ride.pickupTime()).isEqualTo(rideRecord.getPickupTime());
        assertThat(ride.dropoffTime()).isEqualTo(rideRecord.getDropoffTime());
        assertThat(ride.totalFare()).isEqualTo(15001);
        assertThat(ride.farePerPerson()).isEqualTo(7500);
        assertThat(ride.currentMembers()).isEqualTo(2);
    }

    @Test
    void 하차_시각이_같은_기록도_커서로_빠짐없이_이어지고_남의_방과_운행_중인_방은_빠진다() {
        Instant 같은시각 = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Long 이전 = droppedOff(같은시각.minusSeconds(60), 나);
        Long 동률1 = droppedOff(같은시각, 나, 동승자);
        Long 동률2 = droppedOff(같은시각, 동승자, 나);
        Long 이후 = droppedOff(같은시각.plusSeconds(60), 나);
        droppedOff(같은시각, 동승자, 남);
        rideRecords.save(RideRecord.pickUp(savedParty(members(같은시각, 나)), 기사, "강남역", "판교역"));

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
        Long partyId = savedParty(List.of(PartyMember.load(남, 기준.plusSeconds(2)), PartyMember.load(나, 기준.plusSeconds(1)),
                PartyMember.load(동승자, 기준)));

        assertThat(rideRecords.findMemberIds(List.of(partyId)).get(partyId)).containsExactly(동승자, 나, 남);
    }

    @Test
    void 없는_방을_가리키는_기록은_저장되지_않는다() {
        Long 없는방 = track(Long.MAX_VALUE);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ride_record (match_id, taxi_driver_id, departure_place, destination_place, created_at) VALUES (?, ?, ?, ?, now())
                """, 없는방, 기사, "강남역", "판교역"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long droppedOff(Instant dropoffTime, Long... memberIds) {
        Long partyId = savedParty(members(dropoffTime.minusSeconds(3600), memberIds));
        rideRecords.save(RideRecord.load(partyId, 기사, "강남역", "판교역", dropoffTime.minusSeconds(600), dropoffTime, 12000));
        return partyId;
    }

    private List<PartyMember> members(Instant joinedFrom, Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for(int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], joinedFrom.plusSeconds(i)));
        }
        return members;
    }

    private Long savedParty(List<PartyMember> members) {
        Instant now = Instant.now();
        return track(parties.save(Party.load(null, DEPARTURE, DESTINATION, new Radius(100), new Radius(100), "강남역", "판교역",
                new Capacity(3), members, now, PartyStatus.FINISHED, 12000, 25, "_p~iF~ps|U_ulLnnqC", 기사, now, now, 0L)).getId());
    }

    private Long track(Long partyId) {
        created.add(partyId);
        return partyId;
    }
}
