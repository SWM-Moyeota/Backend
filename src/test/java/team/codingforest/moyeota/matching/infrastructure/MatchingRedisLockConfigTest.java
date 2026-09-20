package team.codingforest.moyeota.matching.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingRedisLockConfigTest {

    @Test
    void auto_모드는_앱의_sentinel_설정_유무로_결정한다() {
        assertThat(MatchingRedisLockConfig.resolveMode("auto", "", "")).isEqualTo("single");
        assertThat(MatchingRedisLockConfig.resolveMode("auto", "moyeota", "")).isEqualTo("sentinel");
        assertThat(MatchingRedisLockConfig.resolveMode("auto", "", "127.0.0.1:7001")).isEqualTo("cluster");
        assertThat(MatchingRedisLockConfig.resolveMode("auto", "moyeota", "127.0.0.1:7001")).as("둘 다 있으면 cluster 우선").isEqualTo("cluster");
    }

    @Test
    void 명시한_모드는_그대로_쓴다() {
        assertThat(MatchingRedisLockConfig.resolveMode("single", "moyeota", "127.0.0.1:7001")).isEqualTo("single");
        assertThat(MatchingRedisLockConfig.resolveMode("sentinel", "moyeota", "")).isEqualTo("sentinel");
        assertThat(MatchingRedisLockConfig.resolveMode("cluster", "", "127.0.0.1:7001")).isEqualTo("cluster");
    }
}
