package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 *  출발지 도착 판정(100m)에 쓰는 하버사인 거리
 */
class LocationUnitTest {
    private static final Location GANGNAM = new Location(37.4979, 127.0276);
    // 위도 1도 = 지구 반지름 × π/180. 경선 위에서는 하버사인이 이 값과 정확히 같다
    private static final double METERS_PER_LATITUDE_DEGREE = 6_371_000 * Math.PI / 180;

    @DisplayName("distanceMeters - 같은 지점은 0m 다")
    @Test
    void distanceMeters_samePoint() {

        /*
         * test
         */
        double distance = GANGNAM.distanceMeters(new Location(37.4979, 127.0276));

        /*
         * verify
         */
        assertThat(distance).isZero();
    }

    @DisplayName("distanceMeters - 경선을 따라 위도 0.001도면 약 111.19m 다")
    @Test
    void distanceMeters_alongMeridian() {

        /*
         * settings
         */
        Location north = new Location(37.4989, 127.0276);

        /*
         * test
         */
        double distance = GANGNAM.distanceMeters(north);

        /*
         * verify
         */
        assertThat(distance).isCloseTo(METERS_PER_LATITUDE_DEGREE * 0.001, within(0.001));
        assertThat(distance).isCloseTo(111.19, within(0.01));
    }

    @DisplayName("distanceMeters - 위도 37.5도에서 경도 0.001도는 cos(37.5도)만큼 줄어 약 88.22m 다")
    @Test
    void distanceMeters_alongParallel() {

        /*
         * settings
         */
        Location west = new Location(37.5, 127.0);
        Location east = new Location(37.5, 127.001);

        /*
         * test
         */
        double distance = west.distanceMeters(east);

        /*
         * verify
         */
        assertThat(distance).isCloseTo(METERS_PER_LATITUDE_DEGREE * 0.001 * Math.cos(Math.toRadians(37.5)), within(0.001));
        assertThat(distance).isCloseTo(88.22, within(0.01));
    }

    @DisplayName("distanceMeters - 서울시청에서 부산시청까지 직선 거리는 약 325km 다")
    @Test
    void distanceMeters_seoulToBusan() {

        /*
         * settings
         */
        Location seoulCityHall = new Location(37.5665, 126.9780);
        Location busanCityHall = new Location(35.1796, 129.0756);

        /*
         * test
         */
        double distance = seoulCityHall.distanceMeters(busanCityHall);

        /*
         * verify
         */
        assertThat(distance).isCloseTo(325_111, within(1.0));
    }

    @DisplayName("distanceMeters - 어느 쪽에서 재도 거리가 같다")
    @Test
    void distanceMeters_symmetric() {

        /*
         * settings
         */
        Location pangyo = new Location(37.3948, 127.1112);

        /*
         * test
         */
        double forward = GANGNAM.distanceMeters(pangyo);
        double backward = pangyo.distanceMeters(GANGNAM);

        /*
         * verify
         */
        assertThat(forward).isCloseTo(backward, within(1e-6));
        assertThat(forward).isCloseTo(13_634, within(1.0));
    }
}
