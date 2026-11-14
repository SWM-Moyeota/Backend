package team.codingforest.moyeota.user.social.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.common.exception.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class SocialProofStore {
    private final JdbcTemplate jdbc;

    public String issue(String purpose, Long userId, String value) {
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        jdbc.update("insert into social_proof(token_hash,purpose,user_id,proof_value,expires_at) values (?,?,?,?,?)",
                hash(token), purpose, userId, value, Timestamp.from(Instant.now().plusSeconds(300)));
        return token;
    }

    /** 트랜잭션에 참여하므로 연결 실패 시 증표 소진도 롤백된다. */
    @Transactional
    public String consume(String token, String purpose, Long userId, UserErrorCode error) {
        var rows = jdbc.queryForList("select proof_value,user_id,purpose,expires_at from social_proof where token_hash=? for update", hash(token));
        if (rows.isEmpty()) throw new UserException(error);
        var row = rows.getFirst();
        if (!purpose.equals(row.get("purpose")) || !Objects.equals(userId, row.get("user_id"))
                || !(row.get("expires_at") instanceof Timestamp t ? t.toInstant() : ((java.time.OffsetDateTime)row.get("expires_at")).toInstant()).isAfter(Instant.now())) throw new UserException(error);
        jdbc.update("delete from social_proof where token_hash=?", hash(token));
        return (String) row.get("proof_value");
    }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new UserException(UserErrorCode.SOCIAL_CHALLENGE_INVALID); }
    }
}
