package team.codingforest.moyeota.user.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.common.exception.*;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class SessionRevocations {
    private final JdbcTemplate jdbc;
    public void check(Long userId, Instant issuedAt) {
        var revocations = jdbc.query("select revoked_at from user_session_revocation where user_id=?",
                (rs,n) -> rs.getTimestamp(1).toInstant(), userId);
        if (!revocations.isEmpty() && !issuedAt.isAfter(revocations.getFirst())) throw new UserException(UserErrorCode.TOKEN_INVALID);
    }
}
