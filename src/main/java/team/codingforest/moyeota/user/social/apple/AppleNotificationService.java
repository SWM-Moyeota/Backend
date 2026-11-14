package team.codingforest.moyeota.user.social.apple;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import team.codingforest.moyeota.user.common.exception.*;
import team.codingforest.moyeota.user.social.infrastructure.SocialUserRepository;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;
import team.codingforest.moyeota.user.auth.domain.RefreshTokens;
import team.codingforest.moyeota.user.users.domain.Users;
import java.time.Instant;
import java.sql.Timestamp;

@Service
@RequiredArgsConstructor
public class AppleNotificationService {
    private final AppleClient client;
    private final AppleGrantLocks locks;
    private final SocialUserRepository social;
    private final AppleCredentialStore credentials;
    private final Users users;
    private final RefreshTokens refreshTokens;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;

    public void receive(String payload) {
        var claims = client.verifyNotification(payload);
        if (claims.getId() == null || claims.getId().isBlank()) throw new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID);
        final String type, subject;
        final Instant eventTime;
        try {
            Object raw = claims.get("events");
            var event = raw instanceof String text ? json.readTree(text) : json.valueToTree(raw);
            type = event.path("type").asText(); subject = event.path("sub").asText();
            if (subject.isBlank() || !event.path("event_time").isIntegralNumber()) throw new IllegalArgumentException();
            // 공식 문서는 초 단위지만 밀리초로 전달되는 사례가 보고되어 자릿수로 구분한다(1e11초는 서기 5138년).
            long rawTime = event.path("event_time").asLong();
            eventTime = rawTime >= 100_000_000_000L ? Instant.ofEpochMilli(rawTime) : Instant.ofEpochSecond(rawTime);
            if (eventTime.isAfter(Instant.now().plusSeconds(30)) || eventTime.isBefore(Instant.now().minusSeconds(7*86400))) throw new IllegalArgumentException();
        } catch (Exception e) { throw new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID); }
        for (int attempt=0;attempt<3;attempt++) {
            try {
                transaction.executeWithoutResult(status -> apply(claims.getId(),type,subject,eventTime));
                return;
            } catch (OwnerChanged retry) { /* 가입·탈퇴가 경합하면 새 트랜잭션에서 잠금 순서를 다시 정한다. */ }
        }
        throw new UserException(UserErrorCode.SOCIAL_UNAVAILABLE);
    }

    private void apply(String eventId,String type,String subject,Instant eventTime) {
        // 공식 문서 표기는 account-deleted, 실제 전달 사례로 보고된 표기는 account-delete라 둘 다 받는다.
        if (!type.equals("consent-revoked") && !type.equals("account-deleted") && !type.equals("account-delete")) {
            jdbc.update("insert into apple_notification(event_id,received_at) values (?,CURRENT_TIMESTAMP) on conflict do nothing",eventId);
            return;
        }
        var owners=jdbc.query("select user_id from social_user where social_type='APPLE' and social_id=?",(rs,n) -> rs.getLong(1),subject);
        Long id=owners.isEmpty()?null:owners.getFirst();
        if (id!=null) users.findByIdForUpdate(id);
        locks.lock(subject);
        var current=jdbc.query("select user_id from social_user where social_type='APPLE' and social_id=?",(rs,n) -> rs.getLong(1),subject);
        Long currentId=current.isEmpty()?null:current.getFirst();
        if (!java.util.Objects.equals(id,currentId)) throw new OwnerChanged();
        if (jdbc.update("insert into apple_notification(event_id,received_at) values (?,CURRENT_TIMESTAMP) on conflict do nothing",eventId)==0) return;
        jdbc.update("update apple_grant_lock set revoked_at=? where subject=? and (revoked_at is null or revoked_at<?)",Timestamp.from(eventTime),subject,Timestamp.from(eventTime));
        if (id==null) return; // 가입 중인 PENDING도 위 cutoff로 이후 활성화를 차단한다.
        var verified=jdbc.query("select verified_at from apple_credential where user_id=? and state='ACTIVE'",(rs,n) -> rs.getTimestamp(1).toInstant(),id);
        if (!verified.isEmpty() && eventTime.isBefore(verified.getFirst())) return;
        social.findBySocialTypeAndSocialId(SocialType.APPLE,subject).ifPresent(method -> {
            method.revoke(); social.saveAndFlush(method);
            credentials.markRevoked(id);
            Instant now=Instant.now(); refreshTokens.cancelAllByUserId(id,now);
            if (jdbc.update("update user_session_revocation set revoked_at=? where user_id=?",Timestamp.from(now),id)==0)
                jdbc.update("insert into user_session_revocation(user_id,revoked_at) values (?,?)",id,Timestamp.from(now));
        });
    }
    private static class OwnerChanged extends RuntimeException {}
}
