package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.record.RideRecordService;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 *  약속 확정 → 출발지 도착 → 출발을 실제 서비스와 Postgres 로 돌려, 도착하지 않은 멤버가 노쇼로 저장되고
 *  탑승 기록·탑승 횟수에서 빠지는지 본다. 운영자가 노쇼를 무효로 돌리면(다른 PR - 여기선 컬럼을 직접 채운다) 다시 잡힌다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class PartyAttendancePostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 광주 좌표를 쓴다
    private static final double DEPARTURE_LAT = 35.1595;
    private static final double DEPARTURE_LNG = 126.8526;
    private static final Location DEPARTURE = new Location(DEPARTURE_LAT, DEPARTURE_LNG);
    private static final Location DESTINATION = new Location(35.1468, 126.9200);

    @Autowired PartyService partyService;
    @Autowired RideRecordService rideRecordService;
    @Autowired Parties parties;
    @Autowired JdbcTemplate jdbc;

    // 다른 테스트·이전 실행의 FINISHED 방이 탑승 횟수에 섞이지 않게 매번 새 번호를 쓴다
    private final Long 방장 = ThreadLocalRandom.current().nextLong(3_000_000_000L, 4_000_000_000L);
    private final Long 동승자 = 방장 + 1;
    private final Long 늦은사람 = 방장 + 2;
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
    void 출발하면_도착하지_않은_멤버는_노쇼로_저장되고_탑승_기록과_탑승_횟수에서_빠진다() {
        Long id = confirmedRoomOfThree();

        partyService.arrive(id, 방장, DEPARTURE_LAT, DEPARTURE_LNG);
        partyService.arrive(id, 동승자, DEPARTURE_LAT + 0.0003, DEPARTURE_LNG);   // 약 33m
        partyService.depart(id, 동승자);

        assertThat(parties.findById(id).orElseThrow().getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(column("arrived_at", id, 방장)).isNotNull();
        assertThat(column("arrived_at", id, 동승자)).isNotNull();
        assertThat(column("no_show_at", id, 방장)).isNull();
        assertThat(column("no_show_at", id, 동승자)).isNull();
        assertThat(column("arrived_at", id, 늦은사람)).isNull();
        assertThat(column("no_show_at", id, 늦은사람)).isNotNull();
        assertThat(column("no_show_voided_at", id, 늦은사람)).isNull();

        assertThat(rideRecordService.findMyRides(늦은사람, null, 20).rides()).as("노쇼에게는 탑승 기록이 없다").isEmpty();

        RideRecordResponse.RideRecordItem 방장기록 = onlyRide(방장);
        assertThat(방장기록.partyId()).isEqualTo(id);
        assertThat(방장기록.currentMembers()).as("노쇼는 함께 탄 인원에서도 빠진다").isEqualTo(2);
        assertThat(onlyRide(동승자).partyId()).isEqualTo(id);

        assertThat(parties.countFinishedRides(List.of(방장, 동승자, 늦은사람)))
                .containsOnly(entry(방장, 1), entry(동승자, 1));
    }

    @Test
    void 운영자가_노쇼를_무효로_돌리면_탑승_기록과_탑승_횟수에_다시_잡힌다() {
        Long id = confirmedRoomOfThree();
        partyService.arrive(id, 방장, DEPARTURE_LAT, DEPARTURE_LNG);
        partyService.depart(id, 방장);   // 동승자·늦은사람 노쇼

        jdbc.update("UPDATE user_match_room SET no_show_voided_at = now() WHERE match_id = ? AND user_id = ?", id, 늦은사람);

        assertThat(rideRecordService.findMyRides(동승자, null, 20).rides()).isEmpty();
        assertThat(onlyRide(늦은사람).partyId()).isEqualTo(id);
        assertThat(onlyRide(방장).currentMembers()).as("방장 + 무효로 돌린 늦은사람").isEqualTo(2);

        Map<Long, Integer> counts = parties.countFinishedRides(List.of(방장, 동승자, 늦은사람));
        assertThat(counts).containsOnly(entry(방장, 1), entry(늦은사람, 1));
    }

    @Test
    void 다시_도착을_눌러도_처음_시각이_남고_출발지에서_멀면_도착이_남지_않는다() {
        Long id = confirmedRoomOfThree();
        partyService.arrive(id, 방장, DEPARTURE_LAT, DEPARTURE_LNG);
        Instant 처음 = column("arrived_at", id, 방장);

        partyService.arrive(id, 방장, DEPARTURE_LAT + 0.01, DEPARTURE_LNG);   // 약 1.1km - 이미 도착했으니 다시 재지 않는다

        assertThatThrownBy(() -> partyService.arrive(id, 동승자, DEPARTURE_LAT + 0.001, DEPARTURE_LNG))   // 약 111m
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.TOO_FAR_FROM_DEPARTURE);
        assertThat(column("arrived_at", id, 방장)).isEqualTo(처음);
        assertThat(column("arrived_at", id, 동승자)).isNull();
        assertThat(parties.findById(id).orElseThrow().getVersion()).as("참여 2번·확정·도착 1번").isEqualTo(4);
    }

    /** 방장·동승자·늦은사람 셋이 찬 실시간 방을 방금 지난 시각(30초 전)으로 확정한다 - 바로 도착을 누를 수 있다 */
    private Long confirmedRoomOfThree() {
        Party party = Party.create(방장, DEPARTURE, DESTINATION, "광주송정역", "전남대", new Capacity(3), Instant.now(),
                9000, 20, "_p~iF~ps|U_ulLnnqC");
        party.join(동승자);
        party.join(늦은사람);
        Long id = parties.save(party).getId();
        created.add(id);

        partyService.confirm(id, 방장, Instant.now().minusSeconds(30));

        return id;
    }

    private RideRecordResponse.RideRecordItem onlyRide(Long memberId) {
        List<RideRecordResponse.RideRecordItem> rides = rideRecordService.findMyRides(memberId, null, 20).rides();
        assertThat(rides).hasSize(1);
        return rides.getFirst();
    }

    private Instant column(String name, Long partyId, Long memberId) {
        java.sql.Timestamp value = jdbc.queryForObject("SELECT " + name + " FROM user_match_room WHERE match_id = ? AND user_id = ?",
                java.sql.Timestamp.class, partyId, memberId);
        return value == null ? null : value.toInstant();
    }
}
