package team.codingforest.moyeota.user.passwordreset.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.common.crypto.FieldHasher;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.passwordreset.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ResetRequestsRedis implements ResetRequests {
    private static final String PREFIX = "password-reset:challenge:";
    private static final String QUEUE = "password-reset:queue";
    private static final DefaultRedisScript<Long> ACCEPT = script("accept", Long.class);
    private static final DefaultRedisScript<List> CLAIM = script("claim", List.class);
    private static final DefaultRedisScript<Long> PREPARE = script("prepare", Long.class);
    private static final DefaultRedisScript<Long> FINISH = script("finish", Long.class);
    private static final DefaultRedisScript<List> VERIFY = script("verify", List.class);
    private static final DefaultRedisScript<Long> LIMIT = script("client-limit", Long.class);
    private final StringRedisTemplate redis;
    private final FieldHasher hasher;

    private static <T> DefaultRedisScript<T> script(String file, Class<T> type) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/password-reset/" + file + ".lua"));
        script.setResultType(type);
        return script;
    }

    private <T> T execute(DefaultRedisScript<T> script, List<String> keys, String... args) {
        try {
            T value = redis.execute(script, keys, (Object[]) args);
            if (value == null) throw new UserException(UserErrorCode.PASSWORD_RESET_UNAVAILABLE);
            return value;
        } catch (DataAccessException e) {
            // Redis 예외에는 명령 인수가 포함될 수 있으므로 원문을 로그에 남기지 않는다.
            throw new UserException(UserErrorCode.PASSWORD_RESET_UNAVAILABLE);
        }
    }

    @Override public void accept(ResetRequestJob job) {
        // 모든 API의 IP 제한은 컨트롤러 경계에서 적용한다.
        String pair = hasher.hash(job.loginId() + ":" + job.phoneHash());
        long result = execute(ACCEPT, List.of(QUEUE, "password-reset:latest:" + pair,
                PREFIX + job.requestId(), "sms:cooldown:" + job.phoneHash(), "sms:daily:" + job.phoneHash(),
                "password-reset:account:" + hasher.hash(job.loginId())),
                job.requestId(), job.loginId(), job.phoneHash(), PREFIX);
        if (result == -1) throw new UserException(UserErrorCode.PASSWORD_RESET_UNAVAILABLE);
        if (result > 0) throw new ResetRateLimited(result);
    }

    @Override public Optional<ResetRequestJob> claimNext() {
        List<?> values = execute(CLAIM, List.of(QUEUE), PREFIX);
        if (values.isEmpty()) return Optional.empty();
        return Optional.of(new ResetRequestJob(values.get(0).toString(), values.get(1).toString(),
                values.get(2).toString(), Instant.ofEpochSecond(Long.parseLong(values.get(3).toString()))));
    }

    @Override public boolean prepareCode(String id, VerifiedResetProof proof, String digest) {
        return execute(PREPARE, List.of(PREFIX + id), proof.userId().toString(),
                Long.toString(proof.generation()), digest) == 1;
    }
    @Override public void markSent(String id) { execute(FINISH, List.of(PREFIX + id, QUEUE), id, "READY"); }
    @Override public void invalidate(String id) { execute(FINISH, List.of(PREFIX + id, QUEUE), id, "INVALID"); }

    @Override public VerifiedResetProof verifyAndConsume(String id, String digest) {
        List<?> result = execute(VERIFY, List.of(PREFIX + id), digest);
        if ("LIMITED".equals(result.getFirst())) throw new ResetRateLimited(Long.parseLong(result.get(1).toString()));
        if (!"OK".equals(result.getFirst())) throw new UserException(UserErrorCode.PASSWORD_RESET_VERIFICATION_FAILED);
        return new VerifiedResetProof(Long.valueOf(result.get(1).toString()), result.get(2).toString(),
                Long.parseLong(result.get(3).toString()));
    }

    @Override public void checkClient(String clientKey) {
        long retry = execute(LIMIT, List.of("password-reset:client:" + clientKey));
        if (retry > 0) throw new ResetRateLimited(retry);
    }
}
