package team.codingforest.moyeota.user.social.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * 가입 대기표. 소셜 로그인은 통과했지만 아직 회원이 아닌 사람의 소셜 정보를 휴대폰 인증이 끝날 때까지 잠깐 들고 있는다
 */
public interface SocialSignupTickets {
    void save(String signupToken, SocialProfile profile, Duration ttl);

    /** 조회만 한다 - 닉네임 중복 등으로 가입이 실패해도 소셜 로그인부터 다시 하지 않고 재시도할 수 있다 */
    Optional<SocialProfile> find(String signupToken);

    void delete(String signupToken);
}
