package team.codingforest.moyeota._config;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import team.codingforest.moyeota.dispatch.call.MatchingSweeper;
import team.codingforest.moyeota.matching.party.CompletedPartySweeper;
import team.codingforest.moyeota.matching.sse.infrastructure.PartySseRegistry;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  어떤 스케줄러를 잠그고 어떤 것은 잠그지 않는지가 설계다. 서버가 두 대가 되는 순간부터 효력이 생기므로
 *  어노테이션이 빠지거나 값이 뒤집혀도 한 대짜리 환경에서는 아무 증상이 없다 - 그래서 여기서 고정한다.
 */
class SchedulerLockAnnotationTest {

    @Test
    void DB를_건드리는_스케줄러는_전부_잠근다() {
        assertLocked(IncompleteEventResubmitter.class, "resubmit", "event-resubmit");
        assertLocked(CompletedPartySweeper.class, "sweep", "completed-party-sweep");
        assertLocked(MatchingSweeper.class, "sweep", "matching-sweep");
    }

    @Test
    void SSE_heartbeat_는_잠그지_않는다() {
        // 각 서버가 자기 메모리의 연결에 ping 을 보내는 일이라 서버마다 돌아야 한다. 잠그면 한 대의 연결만 살아남는다
        Method heartbeat = scheduled(PartySseRegistry.class, "heartbeat");

        assertThat(heartbeat.isAnnotationPresent(SchedulerLock.class)).isFalse();
    }

    @Test
    void 잠금_상한은_실행_주기보다_길지_않다() {
        // lockAtMostFor 가 주기보다 길면 돌던 서버가 죽었을 때 다음 실행이 그만큼 늦어진다.
        // lockAtLeastFor 는 주기보다 짧아야 한다 - 길면 매 주기 잠금이 안 풀려 건너뛴다
        for (Method m : List.of(scheduled(IncompleteEventResubmitter.class, "resubmit"),
                scheduled(CompletedPartySweeper.class, "sweep"), scheduled(MatchingSweeper.class, "sweep"))) {
            Duration period = Duration.ofMillis(m.getAnnotation(Scheduled.class).fixedDelay());
            SchedulerLock lock = m.getAnnotation(SchedulerLock.class);

            assertThat(Duration.parse(lock.lockAtLeastFor())).as(lock.name() + " lockAtLeastFor").isLessThan(period);
            assertThat(Duration.parse(lock.lockAtMostFor())).as(lock.name() + " lockAtMostFor").isGreaterThan(Duration.parse(lock.lockAtLeastFor()));
        }
    }

    @Test
    void 잠금_이름은_서로_다르다() {
        List<String> names = List.of(lockName(IncompleteEventResubmitter.class, "resubmit"),
                lockName(CompletedPartySweeper.class, "sweep"), lockName(MatchingSweeper.class, "sweep"));

        assertThat(names).doesNotHaveDuplicates();
    }

    private static void assertLocked(Class<?> type, String method, String expectedName) {
        Method m = scheduled(type, method);
        SchedulerLock lock = m.getAnnotation(SchedulerLock.class);

        assertThat(lock).as(type.getSimpleName() + "." + method + " 에 @SchedulerLock").isNotNull();
        assertThat(lock.name()).isEqualTo(expectedName);
        assertThat(lock.lockAtMostFor()).as("lockAtMostFor 를 비워 두면 기본값(10분)에 묶인다").isNotEmpty();
    }

    private static String lockName(Class<?> type, String method) {
        return scheduled(type, method).getAnnotation(SchedulerLock.class).name();
    }

    private static Method scheduled(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.getName().equals(name) && m.isAnnotationPresent(Scheduled.class))
                .findFirst().orElseThrow(() -> new AssertionError(type.getSimpleName() + "." + name + " 에 @Scheduled 가 없다"));
    }
}
