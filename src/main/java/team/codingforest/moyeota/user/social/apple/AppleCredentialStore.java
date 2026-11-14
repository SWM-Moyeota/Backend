package team.codingforest.moyeota.user.social.apple;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import team.codingforest.moyeota.common.crypto.FieldCipher;
import team.codingforest.moyeota.user.common.exception.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AppleCredentialStore {
    private final JdbcTemplate jdbc;
    private final FieldCipher cipher;
    private final AppleGrantLocks locks;

    /** 외부 교환 뒤 요청이 실패해도 철회할 수 있도록 먼저 독립 커밋한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID stage(AppleClient.Verified verified) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into apple_credential(id,subject,client_id,token_cipher,expires_at,verified_at,state) values (?,?,?,?,?,?,'PENDING')",
                id, verified.profile().socialId(), verified.clientId(), cipher.encrypt(verified.refreshToken()), Timestamp.from(Instant.now().plusSeconds(600)), Timestamp.from(verified.authenticatedAt()));
        return id;
    }
    public void bindSignup(UUID id, String signupToken) {
        jdbc.update("update apple_credential set signup_token=? where id=? and state='PENDING'", team.codingforest.moyeota.user.social.infrastructure.SocialProofStore.hash(signupToken), id);
    }
    public void attachSignup(String token, Long userId) {
        var ids = jdbc.query("select id from apple_credential where signup_token=? and state='PENDING' and expires_at>CURRENT_TIMESTAMP",
                (rs,n) -> rs.getObject(1, UUID.class), team.codingforest.moyeota.user.social.infrastructure.SocialProofStore.hash(token));
        if (ids.isEmpty()) throw new UserException(UserErrorCode.SOCIAL_SIGNUP_EXPIRED);
        attach(ids.getFirst(), userId);
    }
    public void attach(UUID id, Long userId) {
        var subjects = jdbc.query("select subject from apple_credential where id=?",(rs,n) -> rs.getString(1),id);
        if (subjects.isEmpty()) throw new UserException(UserErrorCode.SOCIAL_SIGNUP_EXPIRED);
        String subject = subjects.getFirst(); locks.lock(subject);
        Integer invalid = jdbc.queryForObject("select count(*) from apple_credential c join apple_grant_lock g on g.subject=c.subject where c.id=? and g.revoked_at is not null and c.verified_at<=g.revoked_at",Integer.class,id);
        if (invalid != null && invalid > 0) throw new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID);
        // 새 동의로 얻은 토큰을 유지한다. 기존 토큰을 철회하면 새 동의까지 무효화될 수 있어 구버전은 삭제한다.
        jdbc.update("delete from apple_credential where user_id=? and state='ACTIVE'", userId);
        int changed = jdbc.update("update apple_credential set user_id=?,signup_token=null,expires_at=null,state='ACTIVE' where id=? and state='PENDING' and expires_at>CURRENT_TIMESTAMP", userId, id);
        if (changed != 1) throw new UserException(UserErrorCode.SOCIAL_SIGNUP_EXPIRED);
    }
    public void queueUser(Long userId) {
        jdbc.query("select subject from apple_credential where user_id=?", (rs,n) -> rs.getString(1), userId).stream().sorted().distinct().forEach(locks::lock);
        jdbc.update("update apple_credential set state='REVOKE',retry_at=CURRENT_TIMESTAMP where user_id=?", userId);
    }
    public void markRevoked(Long userId) { jdbc.update("delete from apple_credential where user_id=?", userId); }
}
