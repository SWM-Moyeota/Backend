package team.codingforest.moyeota.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import team.codingforest.moyeota.auth.CustomOAuth2User;
import team.codingforest.moyeota.auth.repository.UserRepository;

import java.util.UUID;

/*
요청이 컨트롤러에 닿기 전에 "지금 요청한 사용자"를 UserContext에 넣어둔다.

[실행 순서]
  JWTFilter(서블릿 필터)  ->  DispatcherServlet  ->  이 인터셉터(preHandle)  ->  컨트롤러
JWTFilter가 먼저 돌며 토큰의 서명·만료·category를 검증하고 SecurityContext에 사용자를 넣어둔다.
그래서 여기서는 토큰을 다시 파싱하지 않고 그 기록만 읽는다. 검증을 두 곳에 두면
한쪽만 고치는 사고가 나므로 읽기만 한다.

[여기서 401을 던지지 않는 이유]
인터셉터는 매칭된 모든 경로에서 돈다. permitAll 경로(로그인 전 조회 등)도 지나가는데,
거기서 401을 던지면 열려 있어야 할 API가 막힌다. 그래서 인증이 없으면 조용히 넘기고,
보호가 필요한 경로는 스프링 시큐리티의 인가 단계가 이미 막는다.
값이 필요한 컨트롤러는 UserContext.get()이 null인지만 확인하면 된다.

[이미 @CurrentUser 리졸버가 있다]
같은 토큰->publicId->user 조회를 CurrentUserArgumentResolver도 한다.
@CurrentUser 를 쓰는 API에서는 이 인터셉터와 리졸버가 각각 한 번씩 조회하게 되니(중복),
용도를 나눠 쓰거나 한쪽으로 통일할지 정하는 게 좋다.
*/
@Component
@RequiredArgsConstructor
public class UserInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        //인증이 없거나 익명 사용자(principal이 "anonymousUser" 문자열)면 채우지 않고 통과.
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomOAuth2User principal)) {
            return true;
        }

        //principal.getName()이 아니라 getUsername()이다. getName()은 null이다.
        //이 값은 로그인 아이디가 아니라 토큰 sub에 담긴 publicId(UUID 문자열)다.
        String subject = principal.getUsername();
        if (subject == null) {
            return true;
        }

        UUID publicId;
        try {
            publicId = UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            //이 구조 이전에 발급된 옛 토큰("google 109...") 형식. 신원으로 쓸 수 없으니 비운 채 통과.
            return true;
        }

        //토큰은 멀쩡한데 DB에 사용자가 없는 경우(탈퇴 등)는 그냥 채우지 않는다.
        //user_id(PK)는 팀 서비스가 받는 값, publicId는 밖으로 내보내거나 로그에 남길 때 쓰는 값.
        userRepository.findByPublicId(publicId)
                .ifPresent(user -> UserContext.set(new LoginUser(user.getUserId(), user.getPublicId())));

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        //스레드 재사용으로 다음 요청에 값이 새지 않도록 예외 여부와 무관하게 항상 비운다.
        UserContext.clear();
    }
}
