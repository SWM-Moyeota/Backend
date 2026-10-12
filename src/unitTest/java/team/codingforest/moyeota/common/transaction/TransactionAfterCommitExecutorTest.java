package team.codingforest.moyeota.common.transaction;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 *  실시간 전파(SSE 신호·채팅 브로드캐스트)를 아웃박스 없이 "커밋 후"에 내보내는 실행기.
 *  DB 없이 Spring 의 트랜잭션 동기화만 직접 열고 닫아 커밋·롤백을 흉내 낸다.
 */
class TransactionAfterCommitExecutorTest {
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final TransactionAfterCommitExecutor executor = new TransactionAfterCommitExecutor(meters);
    private final List<String> ran = new ArrayList<>();

    @AfterEach
    void 동기화_정리() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void 트랜잭션이_없으면_즉시_실행한다() {
        executor.execute("test", () -> ran.add("done"));

        assertThat(ran).containsExactly("done");
    }

    @Test
    void 트랜잭션_안에서는_커밋_전까지_실행하지_않는다() {
        TransactionSynchronizationManager.initSynchronization();

        executor.execute("test", () -> ran.add("done"));

        assertThat(ran).as("커밋 전에 나가면 신호를 받은 클라이언트가 옛 데이터를 조회한다").isEmpty();
    }

    @Test
    void 커밋되면_실행한다() {
        TransactionSynchronizationManager.initSynchronization();
        executor.execute("test", () -> ran.add("done"));

        commit();

        assertThat(ran).containsExactly("done");
    }

    @Test
    void 롤백되면_실행하지_않는다() {
        TransactionSynchronizationManager.initSynchronization();
        executor.execute("test", () -> ran.add("done"));

        rollback();

        assertThat(ran).as("저장되지 않은 변화를 알리면 안 된다").isEmpty();
    }

    @Test
    void 맡긴_순서대로_실행한다() {
        TransactionSynchronizationManager.initSynchronization();
        executor.execute("test", () -> ran.add("first"));
        executor.execute("test", () -> ran.add("second"));

        commit();

        assertThat(ran).containsExactly("first", "second");
    }

    @Test
    void 작업이_예외를_던져도_밖으로_새지_않는다() {
        TransactionSynchronizationManager.initSynchronization();
        executor.execute("test", () -> { throw new IllegalStateException("redis down"); });

        // 커밋은 이미 끝났다 - 여기서 예외가 새면 저장에 성공한 요청이 500 으로 응답한다
        assertThatCode(this::commit).doesNotThrowAnyException();
    }

    @Test
    void 앞_작업이_실패해도_뒤_작업은_실행한다() {
        TransactionSynchronizationManager.initSynchronization();
        executor.execute("test", () -> { throw new IllegalStateException("redis down"); });
        executor.execute("test", () -> ran.add("second"));

        commit();

        assertThat(ran).containsExactly("second");
    }

    @Test
    void 트랜잭션이_없을_때의_예외도_삼킨다() {
        assertThatCode(() -> executor.execute("test", () -> { throw new IllegalStateException("redis down"); }))
                .doesNotThrowAnyException();
    }

    @Test
    void 성공과_실패를_이름별_카운터로_남긴다() {
        executor.execute("party.sse", () -> { });
        executor.execute("party.sse", () -> { });
        executor.execute("party.sse", () -> { throw new IllegalStateException("redis down"); });
        executor.execute("chat.broadcast", () -> { });

        assertThat(count("party.sse", "ok")).isEqualTo(2);
        assertThat(count("party.sse", "fail")).as("유실률 = fail / (ok + fail)").isEqualTo(1);
        assertThat(count("chat.broadcast", "ok")).isEqualTo(1);
    }

    @Test
    void 롤백된_작업은_카운터에_잡히지_않는다() {
        TransactionSynchronizationManager.initSynchronization();
        executor.execute("party.sse", () -> { });

        rollback();

        assertThat(meters.find("after.commit.task").counters()).isEmpty();
    }

    /** AbstractPlatformTransactionManager 가 커밋 직후 하는 일 - 등록된 동기화의 afterCommit 을 부르고 정리한다 */
    private void commit() {
        TransactionSynchronizationUtils.triggerAfterCommit();
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        TransactionSynchronizationManager.clearSynchronization();
    }

    /** 롤백 때는 afterCommit 없이 afterCompletion 만 불린다 */
    private void rollback() {
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        TransactionSynchronizationManager.clearSynchronization();
    }

    private double count(String name, String result) {
        return meters.counter("after.commit.task", "name", name, "result", result).count();
    }
}
