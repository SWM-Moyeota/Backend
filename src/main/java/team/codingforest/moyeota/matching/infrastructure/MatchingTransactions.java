package team.codingforest.moyeota.matching.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.transaction.TransactionDefinition;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class MatchingTransactions {
    private final TransactionTemplate transaction;
    private final JdbcTemplate jdbc;

    public MatchingTransactions(PlatformTransactionManager manager, DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(manager);
        transaction.setTimeout(5);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("매칭 입장은 기존 트랜잭션 밖에서 호출해야 합니다.");
        }
    }

    public <T> T execute(Supplier<T> work) {
        requireNoTransaction();
        return transaction.execute(status -> {
            // PostgreSQL에서는 실제 락 대기 한도도 제한한다. H2는 단위/기존 테스트용이다.
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                if ("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                    try (var statement = connection.createStatement()) {
                        statement.execute("SET LOCAL lock_timeout = '2s'");
                    }
                }
                return null;
            });
            return work.get();
        });
    }
}
