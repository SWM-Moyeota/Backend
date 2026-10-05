package team.codingforest.moyeota.user.auth.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 *  인증 캐시의 Redis 구현. 인증은 모든 요청이 지나는 길이라 Redis 장애가 예외로 새어 나가면 전체 API 가 막힌다 -
 *  어떤 실패든 "없음"으로 돌려 호출부가 DB 로 넘어가게 한다.
 */
class PrincipalRedisCacheTest {
    private static final UUID 공개ID = UUID.fromString("0199a3f2-7c1e-7b3a-9f10-2d4e5a6b7c8d");
    private static final String 키 = "auth:principal:0199a3f2-7c1e-7b3a-9f10-2d4e5a6b7c8d";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final PrincipalRedisCache cache = new PrincipalRedisCache(redis);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void 저장된_내부_번호를_돌려준다() {
        when(values.get(키)).thenReturn("1234");

        assertThat(cache.findUserId(공개ID)).contains(1234L);
    }

    @Test
    void 키가_없으면_비어_있다() {
        when(values.get(키)).thenReturn(null);

        assertThat(cache.findUserId(공개ID)).isEmpty();
    }

    @Test
    void Redis_가_죽어_있으면_예외_대신_비어_있다() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("연결 실패"));

        assertThat(cache.findUserId(공개ID)).isEmpty();
    }

    @Test
    void 값이_숫자가_아니면_없는_것으로_본다() {
        when(values.get(키)).thenReturn("깨진값");

        assertThat(cache.findUserId(공개ID)).isEmpty();
    }

    @Test
    void 저장할_때_만료_시간을_건다() {
        // 만료가 없으면 탈퇴·정지된 사용자가 캐시에 영원히 남는다
        cache.save(공개ID, 1234L);

        verify(values).set(키, "1234", Duration.ofMinutes(10));
    }

    @Test
    void 저장이_실패해도_예외를_던지지_않는다() {
        doThrow(new RedisConnectionFailureException("연결 실패")).when(values).set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> cache.save(공개ID, 1234L)).doesNotThrowAnyException();
    }
}
