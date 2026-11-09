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
import team.codingforest.moyeota.matching.party.domain.MeetingPlan;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.record.RideRecordService;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 *  약속 확정 → 출발지 도착 → 출발을 실제 서비스와 Postgres 로 돌려, 도착 시각이 저장되고 방장이 출발하면 방이 끝나
 *  탑승 기록·탑승 횟수에 잡히는지 본다. 도착한 멤버가 탄 사람(정산 대상)이고, 탑승 기록은 방 단위라 멤버 전원에게 보인다
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
    void 도착한_방장이_출발하면_FINISHED_로_끝나고_도착한_멤버가_탑승자이며_탑승_기록은_멤버_전원에게_남는다() {
        Long id = confirmedRoom(동승자, 늦은사람);

        partyService.arrive(id, 방장, DEPARTURE_LAT, DEPARTURE_LNG);
        partyService.arrive(id, 동승자, DEPARTURE_LAT + 0.0003, DEPARTURE_LNG);   // 약 33m
        partyService.depart(id, 방장);

        Party party = parties.findById(id).orElseThrow();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.riderIds()).as("도착한 멤버가 탄 사람이다").containsExactlyInAnyOrder(방장, 동승자);
        assertThat(column("arrived_at", id, 방장)).isNotNull();
        assertThat(column("arrived_at", id, 동승자)).isNotNull();
        assertThat(column("arrived_at", id, 늦은사람)).isNull();

        RideRecordResponse.RideRecordItem 방장기록 = onlyRide(방장);
        assertThat(방장기록.partyId()).isEqualTo(id);
        assertThat(방장기록.currentMembers()).as("탑승 기록은 방 멤버 전원을 센다").isEqualTo(3);
        assertThat(onlyRide(동승자).partyId()).isEqualTo(id);
        assertThat(onlyRide(늦은사람).partyId()).as("도착하지 않은 멤버에게도 끝난 방의 기록은 남는다").isEqualTo(id);

        assertThat(parties.countFinishedRides(List.of(방장, 동승자, 늦은사람)))
                .containsOnly(entry(방장, 1), entry(동승자, 1), entry(늦은사람, 1));
    }

    @Test
    void 방장이_아닌_멤버는_도착했어도_출발할_수_없고_도착하지_않은_방장도_출발할_수_없다() {
        Long id = confirmedRoom(동승자);
        partyService.arrive(id, 동승자, DEPARTURE_LAT, DEPARTURE_LNG);

        assertThatThrownBy(() -> partyService.depart(id, 동승자))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_PARTY_HOST);
        assertThatThrownBy(() -> partyService.depart(id, 방장))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_ARRIVED);
        assertThat(parties.findById(id).orElseThrow().getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ride_record WHERE match_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void 확정한_방에서_둘_중_하나가_나가면_CANCELED_로_닫힌다() {
        Long id = confirmedRoom(동승자);

        partyService.leave(id, 동승자);

        assertThat(jdbc.queryForObject("SELECT status FROM match_room WHERE id = ?", String.class, id)).isEqualTo("CANCELED");
        Party party = parties.findById(id).orElseThrow();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.memberIds()).containsExactly(방장);
        assertThat(party.getHostId()).isEqualTo(방장);
    }

    @Test
    void 확정한_방에서_셋_중_하나가_나가도_둘이_남으면_그대로_확정_상태다() {
        Long id = confirmedRoom(동승자, 늦은사람);

        partyService.leave(id, 늦은사람);

        Party party = parties.findById(id).orElseThrow();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.memberIds()).containsExactlyInAnyOrder(방장, 동승자);
    }

    @Test
    void 다시_도착을_눌러도_처음_시각이_남고_출발지에서_멀면_도착이_남지_않는다() {
        Long id = confirmedRoom(동승자, 늦은사람);
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

    /**
     *  방장과 넘긴 멤버들이 있는 실시간 방을 확정한 뒤 약속 시각을 방금 지난 시각(30초 전)으로 돌린다 - 바로 도착·출발을 누를 수 있다.
     *  확정은 지금부터 5분 뒤부터만 받으므로 10분 뒤로 확정하고 행을 직접 고친다
     */
    private Long confirmedRoom(Long... others) {
        Party party = Party.create(방장, DEPARTURE, DESTINATION, "광주송정역", "전남대", new Capacity(3), Instant.now(),
                9000, 20, "_p~iF~ps|U_ulLnnqC");
        for(Long other : others) {
            party.join(other, Instant.now());
        }
        Long id = parties.save(party).getId();
        created.add(id);

        partyService.confirm(id, 방장, MeetingPlan.at(Instant.now().plus(Duration.ofMinutes(10))));
        jdbc.update("UPDATE match_room SET departure_at = now() - interval '30 seconds' WHERE id = ?", id);

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
