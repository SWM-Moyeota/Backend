package team.codingforest.moyeota.matching.party.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  출발지 도착 판정(isNearDeparture)은 PostGIS 쿼리라 H2 로 흉내 낼 수 없어 Postgres 에서 확인한다.
 *  위도 0.001도는 경선을 따라 약 111m 라 0.0008도(약 89m)는 100m 안, 0.001도(약 111m)는 밖이다.
 *  예약 방 반경 400m 는 0.0032도(약 355m)가 안, 0.004도(약 444m)가 밖이다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class PartyNearDepartureQueryPostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 대구 좌표를 쓴다
    private static final double DEPARTURE_LAT = 35.8714;
    private static final double DEPARTURE_LNG = 128.6014;
    private static final Location DEPARTURE = new Location(DEPARTURE_LAT, DEPARTURE_LNG);
    private static final Location DESTINATION = new Location(35.8880, 128.6110);

    @Autowired Parties parties;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(id -> {
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    @DisplayName("isNearDeparture - 출발지 바로 그 자리는 반경 안이다")
    @Test
    void isNearDeparture_samePoint() {

        /*
         * settings
         */
        Long id = savedParty();

        /*
         * test
         */
        boolean near = parties.isNearDeparture(id, DEPARTURE_LAT, DEPARTURE_LNG, Party.REALTIME_ARRIVAL_RADIUS_METERS);

        /*
         * verify
         */
        assertThat(near).isTrue();
    }

    @DisplayName("isNearDeparture - 출발지에서 북쪽으로 약 89m(위도 +0.0008도)면 100m 안이다")
    @Test
    void isNearDeparture_inside() {

        /*
         * settings
         */
        Long id = savedParty();

        /*
         * test
         */
        boolean near = parties.isNearDeparture(id, DEPARTURE_LAT + 0.0008, DEPARTURE_LNG, Party.REALTIME_ARRIVAL_RADIUS_METERS);

        /*
         * verify
         */
        assertThat(near).isTrue();
    }

    @DisplayName("isNearDeparture - 출발지에서 북쪽으로 약 111m(위도 +0.001도)면 100m 밖이다")
    @Test
    void isNearDeparture_outside() {

        /*
         * settings
         */
        Long id = savedParty();

        /*
         * test
         */
        boolean near = parties.isNearDeparture(id, DEPARTURE_LAT + 0.001, DEPARTURE_LNG, Party.REALTIME_ARRIVAL_RADIUS_METERS);

        /*
         * verify
         */
        assertThat(near).isFalse();
    }

    @DisplayName("isNearDeparture - 예약 방 반경(400m)이면 북쪽으로 약 355m(위도 +0.0032도)는 안, 약 444m(위도 +0.004도)는 밖이다")
    @Test
    void isNearDeparture_reservationRadius() {

        /*
         * settings
         */
        Long id = savedParty();

        /*
         * test
         */
        boolean inside = parties.isNearDeparture(id, DEPARTURE_LAT + 0.0032, DEPARTURE_LNG, Party.RESERVATION_ARRIVAL_RADIUS_METERS);
        boolean outside = parties.isNearDeparture(id, DEPARTURE_LAT + 0.004, DEPARTURE_LNG, Party.RESERVATION_ARRIVAL_RADIUS_METERS);

        /*
         * verify
         */
        assertThat(inside).isTrue();
        assertThat(outside).isFalse();
    }

    @DisplayName("isNearDeparture - 없는 방이면 오류 없이 false 다")
    @Test
    void isNearDeparture_partyNotFound() {

        /*
         * test
         */
        boolean near = parties.isNearDeparture(-1L, DEPARTURE_LAT, DEPARTURE_LNG, Party.REALTIME_ARRIVAL_RADIUS_METERS);

        /*
         * verify
         */
        assertThat(near).isFalse();
    }

    private Long savedParty() {
        Party party = Party.create(1L, DEPARTURE, DESTINATION, "대구시청", "경북대", new Capacity(3), Instant.now(),
                9000, 20, "_p~iF~ps|U_ulLnnqC");
        Long id = parties.save(party).getId();
        created.add(id);
        return id;
    }
}
