package team.codingforest.moyeota.common.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceAreaTest {

    @Test
    void 서울_좌표는_서비스_지역이다() {
        assertThat(ServiceArea.contains(37.4979, 127.0276)).isTrue();
    }

    @Test
    void 경계_좌표는_서비스_지역에_포함된다() {
        // 부등호를 > 로 잘못 쓰면 제주 남단·서해 끝 좌표가 조용히 막힌다 - 방 생성·즐겨찾기·역지오코딩이 모두 이 규칙을 쓴다
        double[][] boundaries = {{33.0, 124.0}, {33.0, 132.0}, {39.0, 124.0}, {39.0, 132.0}};

        for (double[] point : boundaries) {
            assertThat(ServiceArea.contains(point[0], point[1]))
                    .as("좌표: (%s, %s)", point[0], point[1])
                    .isTrue();
        }
    }

    @Test
    void 경계를_조금이라도_벗어나면_서비스_지역이_아니다() {
        double[][] outside = {{32.999, 127.0}, {39.001, 127.0}, {37.5, 123.999}, {37.5, 132.001}};

        for (double[] point : outside) {
            assertThat(ServiceArea.contains(point[0], point[1]))
                    .as("좌표: (%s, %s)", point[0], point[1])
                    .isFalse();
        }
    }
}
