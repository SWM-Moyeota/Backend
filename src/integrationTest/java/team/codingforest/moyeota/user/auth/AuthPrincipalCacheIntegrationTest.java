package team.codingforest.moyeota.user.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import team.codingforest.moyeota.user.auth.infrastructure.JwtProvider;
import team.codingforest.moyeota.user.common.domain.User;
import team.codingforest.moyeota.user.common.domain.Users;
import team.codingforest.moyeota.user.common.domain.enums.LoginType;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  실제 필터 체인 + 실제 Redis 로 인증 캐시가 이어지는지 본다.
 *  단위 테스트는 가짜 캐시를 꽂으므로 빈 연결·키 형식·만료가 실제로 맞는지는 여기서만 잡힌다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthPrincipalCacheIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired Users users;
    @Autowired JwtProvider jwtProvider;
    @Autowired StringRedisTemplate redisTemplate;

    private final UUID publicId = UUID.randomUUID();

    @AfterEach
    void clean() {
        redisTemplate.delete(key());
    }

    @Test
    void 인증된_요청이_지나가면_공개ID와_내부번호가_Redis에_만료와_함께_남는다() throws Exception {
        User user = users.save(User.from(publicId, LoginType.LOCAL));
        String token = jwtProvider.issuePair(publicId, UUID.randomUUID(), Instant.now()).access();

        mvc.perform(get("/api/v1/users/me/favorite-places").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(redisTemplate.opsForValue().get(key())).isEqualTo(String.valueOf(user.getId()));
        assertThat(redisTemplate.getExpire(key(), TimeUnit.SECONDS)).isBetween(1L, 600L);
    }

    @Test
    void 캐시에_올라간_뒤에도_같은_사용자로_인증된다() throws Exception {
        users.save(User.from(publicId, LoginType.LOCAL));
        String token = jwtProvider.issuePair(publicId, UUID.randomUUID(), Instant.now()).access();

        for(int i = 0; i < 2; i++) {
            mvc.perform(get("/api/v1/users/me/favorite-places").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void 없는_사용자의_토큰은_401이고_캐시에_남지_않는다() throws Exception {
        String token = jwtProvider.issuePair(publicId, UUID.randomUUID(), Instant.now()).access();

        mvc.perform(get("/api/v1/users/me/favorite-places").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        assertThat(redisTemplate.hasKey(key())).isFalse();
    }

    private String key() {
        return "auth:principal:" + publicId;
    }
}
