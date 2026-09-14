package team.codingforest.moyeota.auth.web;

import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import team.codingforest.moyeota.auth.CustomOAuth2User;
import team.codingforest.moyeota.auth.entity.User;
import team.codingforest.moyeota.auth.repository.UserRepository;

import java.util.UUID;

/*
@CurrentUser 가 붙은 파라미터를 채워준다.

[이 클래스가 있는 이유]
토큰 -> publicId -> user_id 로 이어지는 변환이 필요한데,
이걸 컨트롤러마다 적으면 API 개수만큼 복붙되고 한 곳만 빠뜨려도 그 자리가 구멍이 된다.
변환을 여기 한 곳에만 두고, 컨트롤러는 파라미터 하나로 결과만 받는다.

[토큰을 다시 파싱하지 않는다]
여기까지 실행됐다는 것은 JWTFilter가 서명·만료·category 검증을 마치고
SecurityContextHolder에 사용자를 넣어뒀다는 뜻이다. 그 기록만 읽는다.
검증을 두 번 하면 규칙이 두 곳으로 흩어져서 나중에 한쪽만 고치는 사고가 난다.

[왜 필터가 아니라 여기서 DB를 보는가]
필터에서 조회하면 사용자 정보가 필요 없는 API까지 매번 조회하게 된다.
여기서 하면 @CurrentUser 를 실제로 쓰는 API에서만 조회가 일어난다.
*/
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {

        if (!parameter.hasParameterAnnotation(CurrentUser.class)) {
            return false;
        }

        Class<?> type = parameter.getParameterType();

        //둘 다 지원한다. 대부분은 LoginUser면 충분하고,
        //사용자 본체가 필요한 API(마이페이지 등)만 User를 받는다.
        return LoginUser.class.equals(type) || User.class.equals(type);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {

        User user = loadCurrentUser();

        if (User.class.equals(parameter.getParameterType())) {
            return user;
        }

        return new LoginUser(user.getUserId(), user.getPublicId());
    }

    private User loadCurrentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        /*
        인증되지 않은 요청.

        보호된 경로라면 AuthorizationFilter가 이미 막았을 것이므로 여기까지 오지 않는다.
        여기 걸리는 것은 permitAll 경로에 @CurrentUser를 붙인 경우다.
        익명 사용자의 principal은 "anonymousUser" 문자열이라 아래 instanceof에서 걸러진다.
        */
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomOAuth2User principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }

        //JWTFilter가 토큰의 sub(publicId)를 넣어둔 자리다.
        //principal에 저장된 publicId를 꺼낸다. getName()은 프로필 이름용이다.
        String subject = principal.getPublicId();

        //UUID.fromString(null)은 IllegalArgumentException이 아니라 NullPointerException을 던진다.
        //아래 catch에 걸리지 않아 500이 되므로 먼저 막는다.
        if (subject == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token subject");
        }

        UUID publicId;
        try {
            publicId = UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            //이 구조로 바꾸기 전에 발급된 옛 토큰("google 1093847...") 형태.
            //형식부터 다르므로 다시 로그인하라고 알려준다.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token subject");
        }

        /*
        토큰은 멀쩡한데 DB에 사용자가 없는 경우(탈퇴 등).
        access는 서버가 취소할 수 없어 만료 전까지 살아 있으므로 여기서 걸러낸다.

        publicId와 user_id의 짝은 바뀌지 않으므로(User.publicId가 updatable=false)
        부담이 되면 이 조회에 캐시를 얹을 수 있다. 지금은 public_id의 unique 인덱스로 충분하다.
        */
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));
    }
}
