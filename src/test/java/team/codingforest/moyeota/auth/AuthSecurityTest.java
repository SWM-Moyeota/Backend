package team.codingforest.moyeota.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import team.codingforest.moyeota.auth.jwt.JWTUtil;
import team.codingforest.moyeota.auth.jwt.TokenHeaders;
import team.codingforest.moyeota.auth.service.UserService;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
SecurityConfig가 실제로 API를 지키고 있는지 확인하는 테스트.

[왜 필요한가]
SecurityConfig는 한 줄만 잘못 건드려도 조용히 뚫린다.
permitAll 하나를 잘못 넣거나 anyRequest() 위치가 바뀌면 컴파일도 되고 앱도 뜨는데
보호돼야 할 API가 열려버린다. 그 사실은 배포 후에나 드러난다.
그래서 "어떤 요청이 어떤 응답을 받아야 하는가"를 여기에 못박아 둔다.

[MockMvc를 쓰는 이유]
서버를 띄우지 않고도 시큐리티 필터 체인을 그대로 통과시킨다.
즉 여기서 401이 나온다는 것은 실제 요청에서도 401이 나온다는 뜻이다.

[토큰은 진짜로 발급받아 쓴다]
@WithMockUser로 인증된 척하지 않는다. 그렇게 하면 JWTFilter를 건너뛰게 되어
정작 확인하고 싶은 "토큰 검증"이 테스트에서 빠진다.
실제로 회원가입 -> 로그인 -> 헤더로 받은 토큰을 다음 요청에 붙이는, 프론트와 같은 순서를 밟는다.
*/
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("인증·인가가 API에 실제로 걸려 있는지")
class AuthSecurityTest {

    //인증이 필요한 대표 API. 조회와 수정이 같은 경로다.
    private static final String PROTECTED_API = "/api/mypage";

    //테스트끼리 아이디가 겹치면 두 번째부터 409로 실패한다.
    //H2가 인메모리라도 이 테스트 클래스 안에서는 DB가 계속 살아 있기 때문이다.
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JWTUtil jwtUtil;

    // ---------------------------------------------------------------- 인증 없음

    @Test
    @DisplayName("토큰 없이 조회하면 401")
    void 토큰_없이_조회하면_401() throws Exception {

        mvc.perform(get(PROTECTED_API))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    /*
    조회뿐 아니라 수정도 막혀야 한다.
    읽기만 테스트해두면 나중에 누가 쓰기 경로를 permitAll에 넣어도 아무도 모른다.
    */
    @Test
    @DisplayName("토큰 없이 수정하면 401")
    void 토큰_없이_수정하면_401() throws Exception {

        mvc.perform(patch(PROTECTED_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"몰래바꾸기\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    /*
    이 테스트가 이 파일에서 가장 중요하다.

    /api/reservations 는 아직 만들지도 않은 경로다. 그런데도 401이 나와야 한다.
    anyRequest().authenticated() 덕분에 "기본은 잠김"이기 때문이다.
    팀원이 내일 새 컨트롤러를 추가해도 인증이 저절로 걸린다는 뜻이고,
    누군가 이 기본값을 permitAll로 바꾸면 여기서 잡힌다.
    */
    @Test
    @DisplayName("아직 만들지 않은 경로도 기본으로 잠겨 있다")
    void 매핑이_없는_경로도_기본으로_잠겨_있다() throws Exception {

        mvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 인증 성공

    @Test
    @DisplayName("로그인하면 access와 refresh가 헤더로 온다")
    void 로그인하면_토큰이_헤더로_온다() throws Exception {

        String loginId = signup();

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(loginId)))
                .andExpect(status().isOk())
                //프론트가 이 값을 그대로 다음 요청의 Authorization에 넣을 수 있어야 한다.
                .andExpect(header().string(TokenHeaders.ACCESS, org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(header().exists(TokenHeaders.REFRESH));
    }

    @Test
    @DisplayName("유효한 토큰이면 내 정보가 나온다")
    void 유효한_토큰이면_통과한다() throws Exception {

        String access = signupAndLogin();

        mvc.perform(get(PROTECTED_API).header(TokenHeaders.ACCESS, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").isNotEmpty());
    }

    // ---------------------------------------------------------------- 토큰 검증

    @Test
    @DisplayName("서명이 깨진 토큰은 401")
    void 위조된_토큰은_401() throws Exception {

        mvc.perform(get(PROTECTED_API).header(TokenHeaders.ACCESS, "Bearer aaa.bbb.ccc"))
                .andExpect(status().isUnauthorized())
                //위조 토큰에 500이 나오면 "처리되지 않은 경로가 있다"는 신호를 주는 셈이다.
                .andExpect(content().string("invalid access token"));
    }

    /*
    만료는 위조와 다르게 취급해야 한다.
    프론트는 이 본문을 보고 "재발급하면 되는 상황"과 "다시 로그인해야 하는 상황"을 가른다.
    본문 문구를 바꾸면 프론트가 조용히 무한 로그아웃에 빠지므로 여기에 못박아 둔다.
    */
    @Test
    @DisplayName("만료된 토큰은 401 + 재발급하라는 신호")
    void 만료된_토큰은_재발급_신호를_준다() throws Exception {

        //만료시간을 음수로 주면 "이미 만료된" 토큰이 만들어진다. 서명은 우리 키로 정상이다.
        String expired = jwtUtil.createJwt("access", "da4f83ee-e889-4875-b688-70466070c17c",
                UserService.SECURITY_ROLE, -1000L);

        mvc.perform(get(PROTECTED_API).header(TokenHeaders.ACCESS, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("access token expired"));
    }

    /*
    refresh는 수명이 길다(24시간). access 자리에 쓸 수 있다면
    그 긴 토큰 하나로 모든 API를 부를 수 있게 되어 짧은 access를 둔 의미가 사라진다.
    category 클레임이 이걸 막는다.
    */
    @Test
    @DisplayName("refresh 토큰을 access 자리에 쓰면 401")
    void refresh를_access로_쓰면_401() throws Exception {

        String loginId = signup();
        MvcResult login = login(loginId);
        String refresh = login.getResponse().getHeader(TokenHeaders.REFRESH);

        mvc.perform(get(PROTECTED_API).header(TokenHeaders.ACCESS, "Bearer " + refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("invalid access token"));
    }

    // ---------------------------------------------------------------- 인가

    /*
    401과 403은 다른 상황이다.
      401 = 누군지 모르겠다 (로그인해라)
      403 = 누군지는 알지만 권한이 없다
    SecurityConfig의 /my 규칙(hasRole("USER"))이 그 경계다.

    주의: 지금은 모든 사용자의 role이 ROLE_USER 하나뿐이라 실제 서비스에서는 403이 날 일이 없다.
    그래서 여기서는 다른 역할을 가진 토큰을 일부러 만들어 인가 단계가 살아 있는지만 확인한다.
    PASSENGER/DRIVER 구분을 토큰에 싣게 되면 이 테스트를 그 역할로 바꿔야 한다.
    */
    @Test
    @DisplayName("인증은 됐지만 권한이 없으면 401이 아니라 403")
    void 권한이_없으면_403() throws Exception {

        String otherRole = jwtUtil.createJwt("access", "da4f83ee-e889-4875-b688-70466070c17c",
                "ROLE_GUEST", 60_000L);

        mvc.perform(get("/my").header(TokenHeaders.ACCESS, "Bearer " + otherRole))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 신원 위조

    /*
    인가의 절반은 "내 것만 건드릴 수 있는가"다.
    컨트롤러가 사용자 신원을 파라미터에서 읽으면 팔찌를 차고 들어와 남의 좌석에 앉을 수 있다.
    여기서는 A의 토큰에 B의 publicId를 파라미터로 붙여 보내고,
    그래도 A의 정보가 나오는지(=파라미터가 무시되는지) 확인한다.
    */
    @Test
    @DisplayName("남의 publicId를 파라미터로 붙여도 내 정보만 나온다")
    void 신원은_토큰에서만_가져온다() throws Exception {

        String accessA = signupAndLogin();
        String accessB = signupAndLogin();

        String publicIdA = mypagePublicId(accessA);
        String publicIdB = mypagePublicId(accessB);

        assertThat(publicIdA).isNotEqualTo(publicIdB);

        mvc.perform(get(PROTECTED_API)
                        .param("userId", publicIdB)
                        .param("publicId", publicIdB)
                        .header(TokenHeaders.ACCESS, "Bearer " + accessA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(publicIdA));
    }

    // ---------------------------------------------------------------- 로그아웃

    /*
    JWT는 서버가 취소할 수 없다. 그래서 로그아웃은 refresh를 DB에서 지우는 것으로 처리한다.
    지워진 refresh로는 재발급을 받을 수 없어야 하고, 그 시점부터 세션이 끝난다.
    */
    @Test
    @DisplayName("로그아웃하면 그 refresh로 재발급할 수 없다")
    void 로그아웃하면_재발급이_막힌다() throws Exception {

        String loginId = signup();
        String refresh = login(loginId).getResponse().getHeader(TokenHeaders.REFRESH);

        //로그아웃 전에는 재발급이 된다
        mvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refresh)))
                .andExpect(status().isOk());

        //재발급은 refresh를 새 것으로 갈아끼우므로, 로그아웃에는 방금 받은 값을 써야 한다
        String rotated = login(loginId).getResponse().getHeader(TokenHeaders.REFRESH);

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(rotated)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(rotated)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- permitAll

    /*
    잠그는 것만 테스트하면 반대쪽 실수를 못 잡는다.
    로그인 API가 잠기면 아무도 로그인할 수 없고, 그건 401이 아니라 서비스 장애다.
    */
    @Test
    @DisplayName("회원가입은 토큰 없이 할 수 있다")
    void 인증_경로는_토큰_없이_열려_있다() throws Exception {

        //토큰이 없어도 201이 나온다는 것이 이 경로가 permitAll이라는 증거다.
        //(로그인 경로도 열려 있다는 것은 위의 "로그인하면 토큰이 헤더로 온다"가 이미 확인해준다)
        signup();
    }

    @Test
    @DisplayName("Swagger 문서는 토큰 없이 열린다")
    void API_문서는_열려_있다() throws Exception {

        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- 도우미

    //가입만 하고 loginId를 돌려준다. 토큰이 필요 없는 테스트에서 쓴다.
    private String signup() throws Exception {

        String loginId = "tester" + SEQ.incrementAndGet();

        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"testpass1","name":"홍길동",
                                 "birthDate":"2000-05-13","email":"%s@example.com"}
                                """.formatted(loginId, loginId)))
                .andExpect(status().isCreated());

        return loginId;
    }

    //가입 + 로그인까지 하고 access 토큰을 돌려준다.
    private String signupAndLogin() throws Exception {

        MvcResult result = login(signup());

        String header = result.getResponse().getHeader(TokenHeaders.ACCESS);

        //응답 헤더에는 "Bearer "가 붙어 있다. 요청에 다시 붙일 것이므로 여기서는 떼어둔다.
        return header.substring(TokenHeaders.BEARER_PREFIX.length());
    }

    private MvcResult login(String loginId) throws Exception {

        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(loginId)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String mypagePublicId(String access) throws Exception {

        String body = mvc.perform(get(PROTECTED_API).header(TokenHeaders.ACCESS, "Bearer " + access))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.publicId");
    }

    private String loginBody(String loginId) {
        return """
                {"loginId":"%s","password":"testpass1"}
                """.formatted(loginId);
    }

    private String refreshBody(String refresh) {
        return """
                {"refreshToken":"%s"}
                """.formatted(refresh);
    }
}
