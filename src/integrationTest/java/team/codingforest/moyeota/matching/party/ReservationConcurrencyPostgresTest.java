package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.dto.OpenReservationCommand;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.profiles.active=dev", "moyeota.taxi.enabled=false"})
class ReservationConcurrencyPostgresTest {
    private static final long MEMBER_ID = 987_654_322L;

    @Autowired PartyOpener partyOpener;
    @Autowired JdbcTemplate jdbc;

    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        List<Long> partyIds = jdbc.queryForList("SELECT match_id FROM user_match_room WHERE user_id = ?", Long.class, MEMBER_ID);
        jdbc.update("DELETE FROM user_match_room WHERE user_id = ?", MEMBER_ID);
        partyIds.forEach(id -> jdbc.update("DELETE FROM match_room WHERE id = ?", id));
    }

    @RepeatedTest(5)
    void 같은_사용자가_겹치는_예약_방을_동시에_만들면_하나만_성공한다() throws Exception {
        Instant departureAt = Instant.now().plus(Duration.ofDays(1));
        OpenReservationCommand command = new OpenReservationCommand(MEMBER_ID, 36.3504, 127.3845, 36.3320, 127.4342, "대전역", "한밭대", 3, departureAt);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Long> reserve = () -> {
            start.await();
            return partyOpener.reserve(command).id();
        };

        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) futures.add(pool.submit(reserve));
        start.countDown();

        int succeeded = 0;
        int conflicted = 0;
        for (Future<Long> future : futures) {
            try {
                future.get();
                succeeded++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) e.getCause()).getErrorCode()).isEqualTo(MatchingErrorCode.PARTY_TIME_CONFLICT);
                conflicted++;
            }
        }

        assertThat(succeeded).isEqualTo(1);
        assertThat(conflicted).isEqualTo(3);
    }
}
