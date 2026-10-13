package team.codingforest.moyeota.user.deletion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import team.codingforest.moyeota.user.deletion.application.UserDeletionSweeper;
import team.codingforest.moyeota.user.deletion.domain.UserDeletions;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  삭제 쿼리(JPQL)가 실제 DB 에서 보관 기간이 지난 기록만 지우는지 본다
 */
@SpringBootTest
class UserDeletionSweeperIntegrationTest {
    @Autowired UserDeletionSweeper sweeper;
    @Autowired UserDeletions userDeletions;

    @Test
    void 보관_기간이_지난_기록만_지운다() {
        long base = System.currentTimeMillis() % 100_000_000L;
        String expired = "011" + String.format("%08d", base);
        String kept = "016" + String.format("%08d", base);
        Instant now = Instant.now();
        userDeletions.record(expired, now.minus(UserDeletions.RETENTION).minus(Duration.ofHours(1)));
        userDeletions.record(kept, now.minus(UserDeletions.RETENTION).plus(Duration.ofHours(1)));

        sweeper.sweep();

        Instant longAgo = now.minus(Duration.ofDays(365));
        assertThat(userDeletions.existsDeletedSince(expired, longAgo)).as("30일 넘은 기록은 지워진다").isFalse();
        assertThat(userDeletions.existsDeletedSince(kept, longAgo)).as("아직 기간 안이라 남는다").isTrue();
    }
}
