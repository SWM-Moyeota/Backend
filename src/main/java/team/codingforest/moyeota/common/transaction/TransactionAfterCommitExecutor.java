package team.codingforest.moyeota.common.transaction;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionAfterCommitExecutor implements AfterCommitExecutor {

    private static final String METRIC = "after.commit.task";

    private final MeterRegistry meterRegistry;

    @Override
    public void execute(String name, Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runQuietly(name, task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runQuietly(name, task);
            }
        });
    }

    private void runQuietly(String name, Runnable task) {
        try {
            task.run();
            meterRegistry.counter(METRIC, "name", name, "result", "ok").increment();
        } catch (Exception e) {
            meterRegistry.counter(METRIC, "name", name, "result", "fail").increment();
            log.warn("커밋 후 작업 실패 name={}", name, e);
        }
    }
}