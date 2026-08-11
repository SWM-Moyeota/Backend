package team.codingforest.moyeota.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.auth.CustomOAuth2User;
import team.codingforest.moyeota.auth.dto.TokenResponse;
import team.codingforest.moyeota.auth.service.AuthCodeService;
import team.codingforest.moyeota.auth.service.TokenService;

import java.io.IOException;

/*
구글 로그인이 끝난 뒤 프론트로 결과를 넘긴다. 방식이 두 가지라 여기서 갈린다.

  - 코드 교환 방식 (기본, /api/v1/auth/google 로 시작)
      ?code=<30초짜리 난수> 만 넘기고, 프론트가 POST /api/v1/auth/exchange 로
      되돌려주면 그때 토큰을 200 JSON으로 준다.
      이 단계는 브라우저 주소창 이동이라 응답 바디를 쓸 수 없고 남는 통로가 URL뿐인데,
      URL에 JWT를 실으면 24시간짜리 refresh가 주소창/히스토리에 남기 때문이다.

  - 프래그먼트 방식 (예전 방식, /api/v2/auth/google 로 시작)
      #accessToken=...&refreshToken=... 으로 토큰을 그대로 넘긴다.
      서버 왕복이 한 번 없어 간단하지만, 주소창에 토큰이 남는다.

어떻게 갈라내는가.
두 방식 모두 같은 /oauth2/authorization/google 을 거쳐 같은 콜백으로 돌아오므로
성공 핸들러는 이 하나뿐이다. 그래서 로그인을 시작한 컨트롤러가 세션에 표시를 남겨두고,
여기서 그 표시를 보고 갈라준다. 표시가 없으면 기본(코드 교환)이다.

세션을 쓰는 이유.
로그인 시작과 콜백은 서로 다른 요청이라 값을 들고 있을 곳이 필요한데,
OAuth2 로그인은 이미 authorization request를 세션에 보관하며 동작하고 있다(그래서 확실히 존재한다).
쿼리 파라미터로 넘기면 구글을 거치는 동안 사라지고, 쿠키로 하면 지워줄 책임이 하나 더 생긴다.
*/
@Component
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    //어느 방식으로 로그인을 시작했는지 세션에 적어두는 이름과 값.
    //컨트롤러가 이 상수를 그대로 쓰므로 문자열을 양쪽에 따로 적어 어긋나는 일이 없다.
    public static final String FLOW_SESSION_KEY = "moyeota.oauth2.flow";
    public static final String FLOW_FRAGMENT = "fragment";

    private final AuthCodeService authCodeService;
    private final TokenService tokenService;
    private final String redirectUri;

    public CustomSuccessHandler(AuthCodeService authCodeService, TokenService tokenService,
                                @Value("${app.oauth2.redirect-uri}") String redirectUri) {

        this.authCodeService = authCodeService;
        this.tokenService = tokenService;
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();

        String username = principal.getUsername();
        String role = principal.getAuthorities().iterator().next().getAuthority();

        String target = isFragmentFlow(request)
                ? fragmentTarget(username, role)
                : codeTarget(username, role);

        getRedirectStrategy().sendRedirect(request, response, target);
    }

    //세션에 남은 표시를 확인하고 곧바로 지운다.
    //지우지 않으면 같은 브라우저로 다음에 v1으로 로그인해도 계속 예전 방식으로 돌아간다.
    private boolean isFragmentFlow(HttpServletRequest request) {

        //여기서 세션을 새로 만들 이유는 없다. 없으면 v1으로 시작한 것이다.
        HttpSession session = request.getSession(false);

        if (session == null) {
            return false;
        }

        Object flow = session.getAttribute(FLOW_SESSION_KEY);
        session.removeAttribute(FLOW_SESSION_KEY);

        return FLOW_FRAGMENT.equals(flow);
    }

    //코드 교환 방식. 토큰은 만들지 않고 교환권만 넘긴다.
    //사용자가 로그인만 하고 프론트로 돌아오지 않은 경우까지 refresh를 DB에 쌓지 않기 위해서다.
    private String codeTarget(String username, String role) {

        String code = authCodeService.issue(username, role);

        //난수는 프래그먼트(#)가 아니라 쿼리스트링(?)으로 넘긴다.
        //30초짜리 무의미한 값이라 감출 필요가 없고, ?는 서버 라우팅에서도 읽을 수 있어
        //프론트가 SSR이든 CSR이든 동일하게 처리할 수 있다.
        //Base64URL은 URL에서 특별한 뜻을 갖는 문자를 쓰지 않으므로 인코딩 없이 붙여도 안전하다.
        return redirectUri + "?code=" + code;
    }

    //예전 방식. 토큰을 여기서 바로 발급해 URL에 실어 보낸다.
    private String fragmentTarget(String username, String role) {

        //발급 규칙(만료시간, refresh의 DB 저장)은 TokenService 한 곳에만 둔다.
        TokenResponse tokens = tokenService.issue(username, role);

        //쿼리스트링(?)이 아니라 프래그먼트(#)로 넘긴다.
        //프래그먼트는 서버로 전송되지 않으므로 웹서버 액세스 로그나 Referer 헤더에 토큰이 남지 않는다.
        //(다만 주소창과 브라우저 히스토리에는 남는다. 이것이 코드 교환 방식을 만든 이유다.)
        //프론트는 window.location.hash 에서 읽은 뒤 history.replaceState 로 주소창에서 지우면 된다.
        //JWT는 base64url + '.' 만 쓰므로 URL 인코딩 없이 그대로 붙여도 안전하다.
        return redirectUri
                + "#accessToken=" + tokens.accessToken()
                + "&refreshToken=" + tokens.refreshToken();
    }
}
