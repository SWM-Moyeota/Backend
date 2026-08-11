package team.codingforest.moyeota.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.auth.oauth2.CustomSuccessHandler;

import java.io.IOException;

/*
예전 방식(토큰을 URL 프래그먼트로 바로 넘기는 방식)의 구글 로그인 시작점.

/api/v1/auth/google 은 코드 교환 방식이고, 이쪽은 교환 단계 없이 토큰을 바로 받는다.
두 방식을 나란히 두고 비교해볼 수 있도록 경로만 나눠 남겨둔 것이다.

  v1: 브라우저 -> ... -> 프론트?code=난수 -> POST /api/v1/auth/exchange -> 200 JSON으로 토큰
  v2: 브라우저 -> ... -> 프론트#accessToken=...&refreshToken=...  (여기서 끝)

주의: 이건 브라우저가 주소창으로 "이동"할 때만 쓴다.
fetch/axios로 부르면 리다이렉트를 따라가다 구글 도메인에서 CORS로 막힌다.
*/
@Tag(name = "1-2. 인증 (예전 방식)", description = "토큰을 URL 프래그먼트로 바로 받는 구글 로그인")
//이 컨트롤러는 access 토큰 없이 호출되므로 전역 보안 요구사항을 해제한다
@SecurityRequirements
@RestController
@RequestMapping("/api/v2/auth")
public class AuthV2Controller {

    /*
    v1과 v2는 결국 같은 /oauth2/authorization/google 을 거쳐 같은 콜백으로 돌아온다.
    성공 핸들러는 하나뿐이므로, 어느 쪽으로 시작했는지를 여기서 세션에 적어둬야
    CustomSuccessHandler가 갈라낼 수 있다.

    getSession(true) : 없으면 새로 만든다.
    이때 브라우저에 JSESSIONID 쿠키가 내려가고, 이어지는 요청들이 같은 세션을 물고 온다.
    (Security가 STATELESS라 인증 상태는 세션에 담기지 않지만, 세션 자체는 톰캣이 만들어준다.
     OAuth2 로그인도 원래 authorization request를 세션에 보관하며 동작한다.)

    적어둔 표시는 CustomSuccessHandler가 읽자마자 지운다.
    안 지우면 같은 브라우저로 다음에 v1으로 로그인해도 계속 v2로 동작한다.
    */
    @Operation(summary = "구글 로그인 시작 (예전 방식, 브라우저)",
            description = "구글 로그인 화면으로 302 리다이렉트한다. 브라우저 주소창으로 이동할 때만 사용한다. "
                    + "로그인이 끝나면 app.oauth2.redirect-uri 뒤에 "
                    + "#accessToken=...&refreshToken=... 이 붙어 돌아온다. "
                    + "교환 단계(POST /api/v1/auth/exchange)가 없다.")
    @GetMapping("/google")
    public void googleRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {

        request.getSession(true)
                .setAttribute(CustomSuccessHandler.FLOW_SESSION_KEY, CustomSuccessHandler.FLOW_FRAGMENT);

        response.sendRedirect("/oauth2/authorization/google");
    }
}
