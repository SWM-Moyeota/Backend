package team.codingforest.moyeota.matching.party.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.matching.party.PartyService;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.NearbyReservation;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  예약 방 검색(PostGIS 반경 + 한국 날짜 구간)은 H2 로 흉내 낼 수 없어 Postgres 에서 확인한다.
 *  강릉 위도에서 위도 0.0081도는 경선을 따라 약 899m 라 1km 안이고, 0.0108도는 약 1,199m 라 밖이다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class ReservationSearchQueryPostgresTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 다른 테스트가 남긴 방과 섞이지 않게 강릉 좌표를 쓴다
    private static final double DEPARTURE_LAT = 37.7640;
    private static final double DEPARTURE_LNG = 128.8990;
    private static final Location DEPARTURE = new Location(DEPARTURE_LAT, DEPARTURE_LNG);
    private static final Location DEPARTURE_900M = new Location(37.7721, DEPARTURE_LNG);
    private static final Location DEPARTURE_1200M = new Location(37.7748, DEPARTURE_LNG);
    private static final Location DESTINATION = new Location(37.8050, 128.9080);
    private static final Location DESTINATION_900M = new Location(37.8131, 128.9080);
    private static final Location DESTINATION_1200M = new Location(37.8158, 128.9080);
    private static final int RADIUS = 1000;

    @Autowired Parties parties;
    @Autowired PartyService partyService;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(id -> {
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    @DisplayName("searchReservations - 출발지에서 약 900m 인 방은 나오고 약 1.2km 인 방은 빠진다")
    @Test
    void searchReservations_departureRadius() {

        /*
         * settings
         */
        LocalDate date = todayKst().plusDays(1);
        Long near = reserved(DEPARTURE_900M, DESTINATION, kst(date, 12, 0));
        Long far = reserved(DEPARTURE_1200M, DESTINATION, kst(date, 12, 0));

        /*
         * test
         */
        List<Long> found = ids(partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, null, null, date));

        /*
         * verify
         */
        assertThat(found).contains(near).doesNotContain(far);
    }

    @DisplayName("searchReservations - 한국 날짜 하루만 본다 - 그날 00:00·23:59 는 나오고 전날 23:59·다음 날 00:00 은 빠진다")
    @Test
    void searchReservations_kstDayBoundary() {

        /*
         * settings
         */
        LocalDate date = todayKst().plusDays(2);
        Long previousDayLast = reserved(DEPARTURE, DESTINATION, kst(date.minusDays(1), 23, 59));
        Long dayFirst = reserved(DEPARTURE, DESTINATION, kst(date, 0, 0));
        Long dayLast = reserved(DEPARTURE, DESTINATION, kst(date, 23, 59));
        Long nextDayFirst = reserved(DEPARTURE, DESTINATION, kst(date.plusDays(1), 0, 0));

        /*
         * test
         */
        List<Long> found = ownIds(partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, null, null, date));

        /*
         * verify
         */
        assertThat(found).containsExactly(dayFirst, dayLast)
                .doesNotContain(previousDayLast, nextDayFirst);
    }

    @DisplayName("searchReservations - 오늘을 검색하면 이미 출발한 방은 빠지고 아직 출발하지 않은 방만 나온다")
    @Test
    void searchReservations_todayExcludesDeparted() {

        /*
         * settings
         */
        LocalDate today = todayKst();
        Instant now = Instant.now();
        Instant dayStart = kst(today, 0, 0);
        Instant dayEnd = kst(today.plusDays(1), 0, 0);
        // 자정 근처에 돌아도 두 방 모두 오늘 안에 들도록 지금 앞뒤로 남은 구간의 가운데를 쓴다
        Long departed = reserved(DEPARTURE, DESTINATION, midpoint(dayStart, now));
        Long upcoming = reserved(DEPARTURE, DESTINATION, midpoint(now, dayEnd));

        /*
         * test
         */
        List<Long> found = ownIds(partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, null, null, today));

        /*
         * verify
         */
        assertThat(found).containsExactly(upcoming)
                .doesNotContain(departed);
    }

    @DisplayName("searchReservations - 모집 중(ACTIVE)인 예약 방만 나오고 FINISHED·CANCELED 예약 방과 실시간 방은 빠진다")
    @Test
    void searchReservations_onlyActiveReservations() {

        /*
         * settings
         */
        LocalDate date = todayKst().plusDays(1);
        Instant departureAt = kst(date, 15, 0);
        Long active = reserved(DEPARTURE, DESTINATION, departureAt);
        Long finished = reserved(DEPARTURE, DESTINATION, departureAt, PartyStatus.FINISHED);
        Long canceled = reserved(DEPARTURE, DESTINATION, departureAt, PartyStatus.CANCELED);
        // 상태 조건에 걸리지 않게 ACTIVE 로 두고 departure_at 을 같은 날로 채운 실시간 방 - 방 종류 조건만으로 빠지는지 본다
        Long realtime = save(Party.load(null, DEPARTURE, DESTINATION, "강릉역", "경포해변", new Capacity(3), members(1L), 1L, Instant.now(),
                PartyStatus.ACTIVE, 12000, 25, "_p~iF~ps|U_ulLnnqC", 0L, RoomType.REALTIME, departureAt));

        /*
         * test
         */
        List<Long> found = ownIds(partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION.latitude(), DESTINATION.longitude(), date));

        /*
         * verify
         */
        assertThat(found).containsExactly(active)
                .doesNotContain(finished, canceled, realtime);
    }

    @DisplayName("searchReservations - 도착지를 넣으면 도착지도 1km 안인 방이 먼저 오고, 그 안에서 출발 시각·id 순이다")
    @Test
    void searchReservations_withDestination() {

        /*
         * settings
         */
        LocalDate date = todayKst().plusDays(3);
        Long farAt07 = reserved(DEPARTURE, DESTINATION_1200M, kst(date, 7, 0));
        Long nearAt08 = reserved(DEPARTURE_900M, DESTINATION_900M, kst(date, 8, 0));
        Long farAt09 = reserved(DEPARTURE, DESTINATION_1200M, kst(date, 9, 0));
        Long nearAt10 = reserved(DEPARTURE, DESTINATION, kst(date, 10, 0));
        Long nearAt10Later = reserved(DEPARTURE, DESTINATION, kst(date, 10, 0));

        /*
         * test
         */
        List<NearbyReservation> found = own(partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION.latitude(), DESTINATION.longitude(), date));

        /*
         * verify
         */
        assertThat(found).extracting(r -> r.reservation().id())
                .containsExactly(nearAt08, nearAt10, nearAt10Later, farAt07, farAt09);
        assertThat(found).extracting(NearbyReservation::destinationNearby)
                .containsExactly(true, true, true, false, false);
    }

    @DisplayName("searchReservations - 도착지 없이 검색하면 도착지와 상관없이 출발 시각·id 순이고 destinationNearby 는 null 이다")
    @Test
    void searchReservations_withoutDestination() {

        /*
         * settings
         */
        LocalDate date = todayKst().plusDays(3);
        Long farAt07 = reserved(DEPARTURE, DESTINATION_1200M, kst(date, 7, 0));
        Long nearAt08 = reserved(DEPARTURE_900M, DESTINATION_900M, kst(date, 8, 0));
        Long farAt09 = reserved(DEPARTURE, DESTINATION_1200M, kst(date, 9, 0));
        Long nearAt10 = reserved(DEPARTURE, DESTINATION, kst(date, 10, 0));
        Long nearAt10Later = reserved(DEPARTURE, DESTINATION, kst(date, 10, 0));

        /*
         * test
         */
        List<NearbyReservation> found = own(partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, null, null, date));

        /*
         * verify
         */
        assertThat(found).extracting(r -> r.reservation().id())
                .containsExactly(farAt07, nearAt08, farAt09, nearAt10, nearAt10Later);
        assertThat(found).extracting(NearbyReservation::destinationNearby)
                .containsOnlyNulls();
    }

    @DisplayName("searchReservations - 요약은 예약 방 목록과 같은 값(이름·인원·정원·상태·좌표·출발 시각)을 채운다")
    @Test
    void searchReservations_summary() {

        /*
         * settings
         */
        LocalDate date = todayKst().plusDays(1);
        Instant departureAt = kst(date, 18, 30);
        Long id = save(Party.load(null, DEPARTURE_900M, DESTINATION, "강릉역", "경포해변", new Capacity(3), members(1L, 2L), 1L, Instant.now(),
                PartyStatus.ACTIVE, null, null, null, 0L, RoomType.RESERVED, departureAt));

        /*
         * test
         */
        NearbyReservation found = own(partyService.searchReservations(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION.latitude(), DESTINATION.longitude(), date))
                .getFirst();

        /*
         * verify
         */
        ReservationSummary summary = found.reservation();
        assertThat(summary.id()).isEqualTo(id);
        assertThat(summary.departure()).isEqualTo("강릉역");
        assertThat(summary.destination()).isEqualTo("경포해변");
        assertThat(summary.currentMembers()).isEqualTo(2L);
        assertThat(summary.capacity()).isEqualTo(3);
        assertThat(summary.status()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(summary.departureLat()).isEqualTo(DEPARTURE_900M.latitude());
        assertThat(summary.departureLng()).isEqualTo(DEPARTURE_900M.longitude());
        assertThat(summary.destinationLat()).isEqualTo(DESTINATION.latitude());
        assertThat(summary.destinationLng()).isEqualTo(DESTINATION.longitude());
        assertThat(summary.departureAt()).isEqualTo(departureAt);
        assertThat(found.destinationNearby()).isTrue();
    }

    @DisplayName("findReservationsWithinRadius - 상한을 넘으면 정렬한 순서대로 앞에서 자른다")
    @Test
    void findReservationsWithinRadius_limit() {

        /*
         * settings
         */
        // 구간을 직접 넘기는 포트 호출이라 다른 방과 섞이지 않게 먼 날짜를 쓴다
        LocalDate date = LocalDate.of(2099, 1, 1);
        Instant from = kst(date, 0, 0);
        Instant to = kst(date.plusDays(1), 0, 0);
        Long farFirst = reserved(DEPARTURE, DESTINATION_1200M, kst(date, 7, 0));
        Long farSecond = reserved(DEPARTURE, DESTINATION_1200M, kst(date, 8, 0));
        Long nearLast = reserved(DEPARTURE, DESTINATION, kst(date, 9, 0));

        /*
         * test
         */
        List<Long> withDestination = ids(parties.findReservationsWithinRadius(from, to, DEPARTURE, DESTINATION, RADIUS, 2));
        List<Long> withoutDestination = ids(parties.findReservationsWithinRadius(from, to, DEPARTURE, null, RADIUS, 2));

        /*
         * verify
         */
        assertThat(withDestination).containsExactly(nearLast, farFirst);
        assertThat(withoutDestination).containsExactly(farFirst, farSecond);
    }

    @DisplayName("정원이 찬 예약 방은 예약 목록·영역 조회·검색 어디에도 나오지 않는다")
    @Test
    void fullReservation_excludedEverywhere() {

        /*
         * settings
         */
        // 구간을 직접 넘기는 포트 호출이라 다른 방과 섞이지 않게 먼 날짜를 쓴다
        LocalDate date = LocalDate.of(2099, 1, 2);
        Instant from = kst(date, 0, 0);
        Instant to = kst(date.plusDays(1), 0, 0);
        Long 남은방 = save(Party.load(null, DEPARTURE, DESTINATION, "강릉역", "경포해변", new Capacity(3), members(1L, 2L), 1L, Instant.now(),
                PartyStatus.ACTIVE, null, null, null, 0L, RoomType.RESERVED, kst(date, 9, 0)));
        Long 찬방 = save(Party.load(null, DEPARTURE, DESTINATION, "강릉역", "경포해변", new Capacity(3), members(1L, 2L, 3L), 1L, Instant.now(),
                PartyStatus.ACTIVE, null, null, null, 0L, RoomType.RESERVED, kst(date, 9, 0)));

        /*
         * test
         */
        List<Long> list = parties.findReservations(from, to, 100).stream().map(ReservationSummary::id).toList();
        List<Long> bounds = parties.findReservationsWithinBounds(from, to,
                DEPARTURE_LAT - 0.01, DEPARTURE_LAT + 0.01, DEPARTURE_LNG - 0.01, DEPARTURE_LNG + 0.01, 100).stream()
                .map(ReservationSummary::id).toList();
        List<Long> withDestination = ids(parties.findReservationsWithinRadius(from, to, DEPARTURE, DESTINATION, RADIUS, 100));
        List<Long> withoutDestination = ids(parties.findReservationsWithinRadius(from, to, DEPARTURE, null, RADIUS, 100));

        /*
         * verify
         */
        assertThat(parties.findById(찬방).orElseThrow().getStatus()).as("정원이 차도 모집 중이다").isEqualTo(PartyStatus.ACTIVE);
        assertThat(list).contains(남은방).doesNotContain(찬방);
        assertThat(bounds).contains(남은방).doesNotContain(찬방);
        assertThat(withDestination).contains(남은방).doesNotContain(찬방);
        assertThat(withoutDestination).contains(남은방).doesNotContain(찬방);
    }

    private Long reserved(Location departure, Location destination, Instant departureAt) {
        return reserved(departure, destination, departureAt, PartyStatus.ACTIVE);
    }

    private Long reserved(Location departure, Location destination, Instant departureAt, PartyStatus status) {
        return save(Party.load(null, departure, destination, "강릉역", "경포해변", new Capacity(3), members(1L), 1L, Instant.now(),
                status, null, null, null, 0L, RoomType.RESERVED, departureAt));
    }

    private Long save(Party party) {
        Long id = parties.save(party).getId();
        created.add(id);
        return id;
    }

    private static List<PartyMember> members(Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        Instant joinedAt = Instant.now();
        for (int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], joinedAt.plusSeconds(i), null));
        }
        return members;
    }

    /** 이 테스트가 만든 방만 남긴다 - 순서는 그대로 */
    private List<NearbyReservation> own(List<NearbyReservation> found) {
        return found.stream().filter(r -> created.contains(r.reservation().id())).toList();
    }

    private List<Long> ownIds(List<NearbyReservation> found) {
        return ids(own(found));
    }

    private static List<Long> ids(List<NearbyReservation> found) {
        return found.stream().map(r -> r.reservation().id()).toList();
    }

    private static LocalDate todayKst() {
        return LocalDate.now(KST);
    }

    private static Instant kst(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(KST).toInstant();
    }

    private static Instant midpoint(Instant from, Instant to) {
        return from.plus(Duration.between(from, to).dividedBy(2));
    }
}
