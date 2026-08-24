package team.codingforest.moyeota.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import team.codingforest.moyeota.auth.dto.ExchangeRequest;
import team.codingforest.moyeota.auth.dto.LocalLoginRequest;
import team.codingforest.moyeota.auth.dto.LogoutRequest;
import team.codingforest.moyeota.auth.dto.ReissueRequest;
import team.codingforest.moyeota.auth.dto.SignupRequest;
import team.codingforest.moyeota.auth.dto.SignupResponse;
import team.codingforest.moyeota.auth.dto.TokenResponse;
import team.codingforest.moyeota.auth.entity.User;
import team.codingforest.moyeota.auth.jwt.JWTUtil;
import team.codingforest.moyeota.auth.jwt.TokenHeaders;
import team.codingforest.moyeota.auth.repository.RefreshRepository;
import team.codingforest.moyeota.auth.service.AuthCodeService;
import team.codingforest.moyeota.auth.service.TokenService;
import team.codingforest.moyeota.auth.service.UserService;

import java.io.IOException;

@Tag(name = "1. 인증", description = "회원가입 / 로컬 로그인 / 구글 로그인 / 토큰 재발급 / 로그아웃")
//이 컨트롤러는 access 토큰 없이 호출되므로 전역 보안 요구사항을 해제한다
@SecurityRequirements
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;
    private final TokenService tokenService;
    private final AuthCodeService authCodeService;
    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;



    /*
    로컬 회원가입. 아이디/비밀번호와 함께 프로필(이름·나이·생년월일·전화번호·이메일·성별)을 한 번에 받는다.
    구글 로그인은 프로필을 구글이 주지만 로컬은 줄 사람이 없으므로 가입 때 직접 받아야 한다.

    [이 API가 만드는 것]
    user / local_user / user_profile 세 행뿐이다. 딱 여기까지가 "가입"이다.

    토큰은 발급하지 않는다. 따라서 refresh_token 행도 생기지 않는다.
    refresh_token은 로그인의 산물이지 가입의 산물이 아니기 때문이다.
    가입을 마친 프론트는 POST /login을 따로 불러야 토큰을 받는다.

    (예전에는 여기서 곧바로 tokenService.issue()를 불러 자동 로그인시켰다.
     그때는 가입하는 순간 refresh_token 행까지 같이 생겼다. 지금은 그러지 않는다.)

    @Valid : SignupRequest에 붙은 검증 애노테이션을 실제로 동작시키는 스위치.
    이게 없으면 애노테이션이 다 무시되고 빈 문자열도 그대로 저장된다.
    */
    @Operation(summary = "회원가입 (로컬)",
            description = "아이디/비밀번호로 계정을 만든다. user / local_user / user_profile 세 행이 한 번에 만들어진다. "
                    + "토큰은 주지 않으므로(refresh_token 행도 생기지 않는다) 가입 후 POST /login을 따로 호출해야 한다. "
                    + "입력 형식이 틀리면 400, 이미 쓰는 아이디면 409.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {

        userService.signupLocal(request);

        //여기까지 왔다면 세 행이 모두 만들어진 것이므로 성공이다.
        //실패는 이 자리로 오지 않는다. signupLocal이 400/409를 던지고 끝난다.
        return new SignupResponse(true);
    }

    /*
    가입된 아이디/비밀번호를 우리 서비스의 JWT로 교환해준다.

    토큰은 본문이 아니라 응답 헤더로 나간다.
      Authorization: Bearer <accessToken>
      Refresh-Token: <refreshToken>
    본문은 비어 있다(200).

    [프론트가 알아야 할 것]
    이 헤더들은 CORS 기본 규칙상 브라우저가 자바스크립트에게 보여주지 않는다.
    서버가 Access-Control-Expose-Headers 로 "읽어도 된다"고 알려줘야 하고,
    그 설정은 SecurityConfig의 CORS 쪽에 있다. 헤더 이름을 바꾼다면 그쪽도 같이 고쳐야 한다.

      const res = await fetch('/api/v1/auth/login', {...});
      const access  = res.headers.get('Authorization');   // "Bearer eyJ..."
      const refresh = res.headers.get('Refresh-Token');

    재발급(POST /reissue)도 같은 헤더 방식이다.
    회원가입(POST /signup)은 토큰을 아예 주지 않고, 구글 코드 교환(POST /login/exchange)만 아직 본문으로 준다.
    */
    @Operation(summary = "로컬 로그인",
            description = "아이디와 비밀번호로 토큰을 받는다. "
                    + "토큰은 응답 본문이 아니라 Authorization / Refresh-Token 헤더로 나간다(본문은 비어 있음). "
                    + "Authorization 값에는 Bearer 접두사가 붙어 있어 그대로 다음 요청에 넣으면 된다. "
                    + "아이디가 없거나 비밀번호가 틀리면 둘 다 401(어느 쪽인지 알려주지 않는다).")
    @ApiResponse(responseCode = "200", description = "로그인 성공. 토큰은 헤더에 있다.",
            headers = {
                    @Header(name = TokenHeaders.ACCESS, description = "Bearer <accessToken>",
                            schema = @Schema(type = "string")),
                    @Header(name = TokenHeaders.REFRESH, description = "refreshToken (Bearer 없음)",
                            schema = @Schema(type = "string"))
            },
            content = @Content)
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LocalLoginRequest request) {

        User user = userService.authenticateLocal(request.loginId(), request.password());

        TokenResponse tokens = tokenService.issue(user.getPublicId().toString(), UserService.SECURITY_ROLE);

        return ResponseEntity.ok()
                .header(TokenHeaders.ACCESS, TokenHeaders.BEARER_PREFIX + tokens.accessToken())
                .header(TokenHeaders.REFRESH, tokens.refreshToken())
                .build();
    }

    /*
    브라우저용 구글 로그인 시작점.

    실제 로그인 절차는 Spring Security가 만들어주는 /oauth2/authorization/google 이 담당한다.
    여기서는 302 Found로 그 주소를 가리키기만 한다. 이후는 브라우저가 알아서 따라간다.

      브라우저 -> GET /api/v1/auth/google        (이 메서드, 302)
              -> GET /oauth2/authorization/google (Security 필터, 302)
              -> 구글 로그인 화면
              -> GET /login/oauth2/code/google    (콜백)
              -> CustomSuccessHandler가 프론트로 일회용 코드를 들려 보냄 (302, ?code=...)
      프론트   -> POST /api/v1/auth/exchange      (아래 메서드, 200 JSON으로 토큰 수령)

    굳이 한 단계를 더 두는 이유는 인증 관련 진입점을 /api/v1/auth 아래로 모아두기 위해서다.
    프론트는 우리 API 주소만 알면 되고, Security의 내부 경로가 바뀌어도 여기만 고치면 된다.

    주의: 이건 브라우저가 주소창으로 "이동"할 때만 쓴다.
    fetch/axios로 부르면 리다이렉트를 따라가다 구글 도메인에서 CORS로 막힌다.
    (window.location.href = '...' 로 이동시킬 것)
    */
    @Operation(summary = "구글 로그인 시작 (브라우저)",
            description = "구글 로그인 화면으로 302 리다이렉트한다. 브라우저 주소창으로 이동할 때만 사용한다. "
                    + "로그인이 끝나면 app.oauth2.redirect-uri 로 토큰이 붙어 돌아온다.")
    @GetMapping("/login/google")
    public void googleRedirect(HttpServletResponse response) throws IOException {

        //Security가 등록한 기본 경로. registrationId(google)는 application.properties의
        //spring.security.oauth2.client.registration.google 에서 온다.
        response.sendRedirect("/oauth2/authorization/google");
    }

    /*
    구글 로그인의 마지막 단계. 일회용 코드를 진짜 토큰으로 바꿔준다.

    구글 로그인이 끝나면 브라우저는 app.oauth2.redirect-uri 로 ?code=... 를 달고 돌아온다.
    프론트는 그 code를 쿼리스트링에서 읽어 이 API로 보내고, 여기서 200 JSON으로 토큰을 받는다.

    주의: 로컬 로그인(POST /login)은 토큰을 응답 헤더로 주도록 바뀌었고 이쪽은 아직 본문이다.
    둘의 응답 모양이 다르므로 프론트의 토큰 저장 로직도 갈라져 있어야 한다.

    코드는 30초만 살고 한 번 쓰면 사라진다.
    그래서 주소창에 남은 URL을 나중에 누가 다시 열어도 토큰이 나오지 않는다.
    */
    @Operation(summary = "구글 로그인 코드 교환",
            description = "구글 로그인 후 리다이렉트 URL에 붙어 온 code를 accessToken/refreshToken으로 바꾼다. "
                    + "code는 30초간 유효하고 1회만 쓸 수 있다. "
                    + "만료됐거나 이미 사용한 code면 401.")
    @PostMapping("/login/exchange")
    public TokenResponse exchange(@RequestBody ExchangeRequest request) {

        AuthCodeService.UsernameAndRole owner = authCodeService.consume(request.code());

        //토큰 발급 규칙은 로컬 로그인과 동일하게 TokenService 한 곳에서만 처리한다.
        return tokenService.issue(owner.username(), owner.role());
    }

    /*
    access가 만료되어 refresh를 들고 여기로 옴.

    토큰은 본문이 아니라 응답 헤더로 나간다(로컬 로그인 POST /login과 같은 방식).
      Authorization: Bearer <accessToken>
      Refresh-Token: <refreshToken>
    본문은 비어 있다(200).

    [프론트가 알아야 할 것]
    Refresh Rotation이라 refreshToken도 새 값으로 바뀐다.
    Authorization만 갈아끼우고 Refresh-Token을 옛 값 그대로 두면 다음 재발급이 401로 튕긴다.
    두 헤더를 반드시 함께 갱신 저장해야 한다.

    이 헤더들도 CORS 기본 규칙상 브라우저가 자바스크립트에게 그냥은 보여주지 않는다.
    SecurityConfig의 CORS가 Access-Control-Expose-Headers로 열어주고 있고,
    로그인과 같은 헤더 이름(TokenHeaders)을 쓰므로 따로 추가할 것은 없다.
    */
    @Operation(summary = "토큰 재발급",
            description = "access가 만료(401 access token expired)됐을 때 refresh로 새 토큰을 받는다. "
                    + "토큰은 응답 본문이 아니라 Authorization / Refresh-Token 헤더로 나간다(본문은 비어 있음). "
                    + "Refresh Rotation이라 refreshToken도 새 값으로 바뀌므로 두 헤더를 반드시 함께 갱신 저장할 것. "
                    + "로그아웃했거나 이미 사용한 refresh는 401.")
    @ApiResponse(responseCode = "200", description = "재발급 성공. 토큰은 헤더에 있다.",
            headers = {
                    @Header(name = TokenHeaders.ACCESS, description = "Bearer <accessToken>",
                            schema = @Schema(type = "string")),
                    @Header(name = TokenHeaders.REFRESH, description = "refreshToken (Bearer 없음)",
                            schema = @Schema(type = "string"))
            },
            content = @Content)
    @PostMapping("/reissue")
    public ResponseEntity<Void> reissue(@RequestBody ReissueRequest request){
        String refresh=request.refreshToken();
        if(refresh==null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refresh token null");
        }

        //서명·형식부터 확인한다. 먼저 걸러내지 않으면 아래 파싱에서 예외가 나 500이 된다(401이어야 한다).
        if(!jwtUtil.isSignatureValid(refresh)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
        }

        if(jwtUtil.isExpired(refresh)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token expired");
        }

        //access를 refresh 자리에 넣은 경우 차단
        if(!"refresh".equals(jwtUtil.getCategory(refresh))){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
        }

        //로그아웃됐거나 이미 사용된 refresh인지 DB로 확인
        if(!refreshRepository.existsByRefreshToken(refresh)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"invalid refresh token");
        }

        String username=jwtUtil.getUsername(refresh);
        String role=jwtUtil.getRole(refresh);

        //Refresh Rotation : 기존 refresh를 폐기하고 새로 발급.
        //refresh_token은 user_id가 PK라 사용자당 한 행이므로, issue()가 그 행을 덮어쓰면서 옛 값이 사라진다.
        //따로 삭제할 필요가 없다.
        TokenResponse tokens = tokenService.issue(username,role);

        //헤더 이름과 Bearer 접두사는 로그인과 똑같이 TokenHeaders에서 가져온다.
        //프론트가 로그인 응답과 재발급 응답을 같은 코드로 처리할 수 있게 하기 위해서다.
        return ResponseEntity.ok()
                .header(TokenHeaders.ACCESS, TokenHeaders.BEARER_PREFIX + tokens.accessToken())
                .header(TokenHeaders.REFRESH, tokens.refreshToken())
                .build();
    }

    //앱은 호출 후 자기 저장소의 토큰도 지운다
    @Operation(summary = "로그아웃",
            description = "DB에서 refresh를 삭제해 재발급을 막는다. "
                    + "이미 발급된 access는 서버가 취소할 수 없으므로 최대 10분간 유효하다. "
                    + "앱은 호출 후 자기 저장소의 토큰도 지워야 한다.")
    @PostMapping("/logout")
    public void logout(@RequestBody LogoutRequest request){
        refreshRepository.deleteByRefreshToken(request.refreshToken());
    }


}


//구글 SDK