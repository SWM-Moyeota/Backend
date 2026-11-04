package team.codingforest.moyeota.matching.party.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@RequiredArgsConstructor
public class H2AdvisoryLockFunction implements InitializingBean {
    private final DataSource dataSource;

    public static String noop(long key) {
        return "";
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (!"H2".equals(connection.getMetaData().getDatabaseProductName())) return;

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE ALIAS IF NOT EXISTS pg_advisory_xact_lock FOR \"" + H2AdvisoryLockFunction.class.getName() + ".noop\"");
            }
        }
    }
}
