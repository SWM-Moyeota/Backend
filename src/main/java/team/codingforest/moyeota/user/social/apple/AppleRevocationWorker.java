package team.codingforest.moyeota.user.social.apple;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import team.codingforest.moyeota.common.crypto.FieldCipher;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** DB 행이 철회 outbox이다. 실패 원문을 기록하지 않고 ID와 시도 횟수만 남긴다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppleRevocationWorker {
    private final JdbcTemplate jdbc;
    private final FieldCipher cipher;
    private final AppleClient client;
    private final AppleGrantLocks locks;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    @SchedulerLock(name = "apple-revoke", lockAtMostFor = "PT15M")
    public void run() {
        jdbc.update("delete from social_proof where expires_at<CURRENT_TIMESTAMP");
        jdbc.update("update apple_credential set state='REVOKE' where state='PENDING' and expires_at<CURRENT_TIMESTAMP");
        var rows = jdbc.queryForList("select id,client_id,token_cipher,attempts from apple_credential where state='REVOKE' and retry_at<CURRENT_TIMESTAMP order by retry_at limit 20");
        for (var row : rows) {
            UUID id = (UUID) row.get("id");
            transactions.executeWithoutResult(status -> process(id));
        }
    }
    private void process(UUID id) {
        var subjects = jdbc.query("select subject from apple_credential where id=?",(rs,n) -> rs.getString(1),id);
        if (subjects.isEmpty()) return;
        String subject = subjects.getFirst(); locks.lock(subject);
        var current = jdbc.queryForList("select client_id,token_cipher,attempts from apple_credential where id=? and state='REVOKE'",id);
        if (current.isEmpty()) return;
        // 다른 성공한 동의 또는 진행 중인 새 인증이 있으면 이전 토큰을 원격 철회하지 않는다.
        Integer live = jdbc.queryForObject("select count(*) from apple_credential where subject=? and (state='ACTIVE' or (state='PENDING' and expires_at>CURRENT_TIMESTAMP))",Integer.class,subject);
        if (live != null && live > 0) { jdbc.update("delete from apple_credential where id=?",id); return; }
        var row = current.getFirst(); int attempts = ((Number)row.get("attempts")).intValue() + 1;
        try {
            client.revoke((String)row.get("client_id"),cipher.decrypt((String)row.get("token_cipher")));
            jdbc.update("delete from apple_credential where subject=? and state='REVOKE'",subject);
        } catch (RuntimeException e) {
            jdbc.update("update apple_credential set attempts=?,retry_at=? where id=?",attempts,
                    Timestamp.from(Instant.now().plusSeconds(Math.min(86400,60L << Math.min(attempts,10)))),id);
            log.warn("Apple 토큰 철회 재시도 예약 id={} attempts={}",id,attempts);
        }
    }
}
