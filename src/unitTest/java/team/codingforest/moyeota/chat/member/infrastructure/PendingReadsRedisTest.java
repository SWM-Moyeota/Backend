package team.codingforest.moyeota.chat.member.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PendingReadsRedisTest {

    private static final String USERS_KEY = "chat:read:users";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SetOperations<String, String> setOperations;
    @InjectMocks
    private PendingReadsRedis pendingReads;

    @Test
    void 가져오기에_실패하면_꺼낸_사람을_명단에_되돌린다() {
        given(redisTemplate.execute(any(SessionCallback.class)))
                .willThrow(new RedisConnectionFailureException("풀 대기 시간 초과"));
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        assertThatThrownBy(() -> pendingReads.takeAll(List.of(7L, 12L)))
                .isInstanceOf(RedisConnectionFailureException.class);

        verify(setOperations).add(USERS_KEY, "7", "12");
    }

    @Test
    void 되돌리기도_실패하면_원래_예외를_던지고_되돌리기_실패를_덧붙인다() {
        RedisConnectionFailureException takeFailure = new RedisConnectionFailureException("가져오기 실패");
        given(redisTemplate.execute(any(SessionCallback.class))).willThrow(takeFailure);
        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.add(any(), any(String[].class)))
                .willThrow(new RedisConnectionFailureException("Redis 다운"));

        assertThatThrownBy(() -> pendingReads.takeAll(List.of(7L)))
                .isSameAs(takeFailure);
        assertThat(takeFailure.getSuppressed()).hasSize(1);
    }
}
