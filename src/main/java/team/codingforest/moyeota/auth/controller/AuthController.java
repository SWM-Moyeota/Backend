package team.codingforest.moyeota.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
import team.codingforest.moyeota.auth.dto.TokenResponse;
import team.codingforest.moyeota.auth.entity.User;
import team.codingforest.moyeota.auth.jwt.JWTUtil;
import team.codingforest.moyeota.auth.repository.RefreshRepository;
import team.codingforest.moyeota.auth.service.AuthCodeService;
import team.codingforest.moyeota.auth.service.TokenService;
import team.codingforest.moyeota.auth.service.UserService;

import java.io.IOException;

@Tag(name = "1. 인증", description = "회원가입 / 로컬 로그인 / 구글 로그인 / 토큰 재발급 / 로그아웃")
//이 컨트롤러는 access 토큰 없이 호출되므로 전역 보안 요구사항을 해제한다
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;
    private final TokenService tokenService;
    private final AuthCodeService authCodeService;
    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;

    public AuthController(UserService userService,
                          TokenService tokenService, AuthCodeService authCodeService,
                          JWTUtil jwtUtil,  RefreshRepository refreshRepository){

        this.userService=userService;
        this.tokenService=tokenService;
        this.authCodeService=authCodeService;
        this.jwtUtil=jwtUtil;
        this.refreshRepository=refreshRepository;
    }

    /*
    로컬 회원가입. 아이디/비밀번호와 함께 프로필(이름·나이·생년월일·전화번호·이메일·성별)을 한 번에 받는다.
    구글 로그인은 프로필을 구글이 주지만 로컬은 줄 사람이 없으므로 가입 때 직접 받아야 한다.

    가입에 성공하면 곧바로 토큰을 발급한다.
    가입 직후 다시 로그인 화면으로 보내는 것은 사용자 입장에서 같은 정보를 두 번 입력하는 셈이라서다.

    @Valid : SignupRequest에 붙은 검증 애노테이션을 실제로 동작시키는 스위치.
    이게 없으면 애노테이션이 다 무시되고 빈 문자열도 그대로 저장된다.
    */
    @Operation(summary = "회원가입 (로컬)",
            description = "아이디/비밀번호로 가입하고 accessToken/refreshToken을 바로 돌려준다. "
                    + "user / local_user / user_profile 세 행이 한 번에 만들어진다. "
                    + "입력 형식이 틀리면 400, 이미 쓰는 아이디면 409.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public TokenResponse signup(@Valid @RequestBody SignupRequest request) {

        User user = userService.signupLocal(request);

        //구글 로그인과 똑같이 publicId를 subject로 쓴다.
        //덕분에 이후 API(/api/me 등)는 로컬인지 소셜인지 구분할 필요가 없다.
        return tokenService.issue(user.getPublicId().toString(), UserService.SECURITY_ROLE);
    }

    //가입된 아이디/비밀번호를 우리 서비스의 JWT로 교환해준다
    @Operation(summary = "로컬 로그인",
            description = "아이디와 비밀번호로 accessToken/refreshToken을 받는다. "
                    + "아이디가 없거나 비밀번호가 틀리면 둘 다 401(어느 쪽인지 알려주지 않는다).")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LocalLoginRequest request) {

        User user = userService.authenticateLocal(request.loginId(), request.password());

        return tokenService.issue(user.getPublicId().toString(), UserService.SECURITY_ROLE);
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
    로컬 로그인(POST /login)과 응답 모양이 똑같으므로 프론트의 저장 로직도 하나로 쓸 수 있다.

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

    //access가 만료되어 refresh를 들고 여기로 옴
    @Operation(summary = "토큰 재발급",
            description = "access가 만료(401 access token expired)됐을 때 refresh로 새 토큰을 받는다. "
                    + "Refresh Rotation이라 refreshToken도 새 값으로 바뀌므로 반드시 갱신 저장할 것. "
                    + "로그아웃했거나 이미 사용한 refresh는 401.")
    @PostMapping("/reissue")
    public TokenResponse reissue(@RequestBody ReissueRequest request){
        String refresh=request.refreshToken();
        if(refresh==null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refresh token null");
        }

        if(jwtUtil.isExpired(refresh)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token expired");
        }

        //access를 refresh 자리에 넣은 경우 차단
        if(!"refresh".equals(jwtUtil.getCategory(refresh))){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
        }

        //로그아웃됐거나 이미 사용된 refresh인지 DB로 확인
        if(!refreshRepository.existsByRefresh(refresh)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"invalid refresh token");
        }

        String username=jwtUtil.getUsername(refresh);
        String role=jwtUtil.getRole(refresh);

        //Refresh Rotation : 기존 refresh를 폐기하고 새로 발급
        refreshRepository.deleteByRefresh(refresh);

        return tokenService.issue(username,role);
    }

    //앱은 호출 후 자기 저장소의 토큰도 지운다
    @Operation(summary = "로그아웃",
            description = "DB에서 refresh를 삭제해 재발급을 막는다. "
                    + "이미 발급된 access는 서버가 취소할 수 없으므로 최대 10분간 유효하다. "
                    + "앱은 호출 후 자기 저장소의 토큰도 지워야 한다.")
    @PostMapping("/logout")
    public void logout(@RequestBody LogoutRequest request){
        refreshRepository.deleteByRefresh(request.refreshToken());
    }


}


//구글 SDK