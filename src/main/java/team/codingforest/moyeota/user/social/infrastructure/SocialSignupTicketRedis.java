package team.codingforest.moyeota.user.social.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.social.domain.SocialProfile;
import team.codingforest.moyeota.user.social.domain.SocialSignupTickets;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SocialSignupTicketRedis implements SocialSignupTickets {
    private static final String SIGNUP = "social:signup:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(String signupToken, SocialProfile profile, Duration ttl) {
        redisTemplate.opsForValue().set(SIGNUP + signupToken, objectMapper.writeValueAsString(profile), ttl);
    }

    @Override
    public Optional<SocialProfile> find(String signupToken) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(SIGNUP + signupToken))
                .map(json -> objectMapper.readValue(json, SocialProfile.class));
    }

    @Override
    public void delete(String signupToken) {
        redisTemplate.delete(SIGNUP + signupToken);
    }
}
