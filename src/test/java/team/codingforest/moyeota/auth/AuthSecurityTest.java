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
import team.codingforest.moyeota.auth.service.AuthCodeService;

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

    //구글 로그인 성공 핸들러가 하는 일(일회용 코드 발급)을 테스트에서 대신하기 위해 쓴다.
    @Autowired
    private AuthCodeService authCodeService;

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

    /*
    구글 로그인의 마지막 단계(코드 교환)도 로컬 로그인과 같은 헤더 모양으로 토큰을 줘야 한다.
    프론트가 두 로그인의 토큰 저장 코드를 하나로 쓸 수 있는지가 이 테스트에 걸려 있다.

    구글 화면을 실제로 거칠 수는 없으므로, 구글 로그인 성공 직후 CustomSuccessHandler가 하는 일
    (AuthCodeService.issue로 일회용 코드 발급)을 여기서 직접 한 뒤 그 코드를 교환한다.
    */
    @Test
    @DisplayName("구글 코드 교환도 access와 refresh가 헤더로 온다")
    void 코드_교환하면_토큰이_헤더로_온다() throws Exception {

        String publicId = mypagePublicId(signupAndLogin());
        String code = authCodeService.issue(publicId);

        MvcResult exchanged = mvc.perform(post("/api/v1/auth/login/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(code)))
                .andExpect(status().isOk())
                .andExpect(header().string(TokenHeaders.ACCESS, org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(header().exists(TokenHeaders.REFRESH))
                //토큰은 헤더로만 나간다. 본문에도 실려 있으면 프론트가 어느 쪽을 믿어야 할지 갈린다.
                .andExpect(content().string(""))
                .andReturn();

        //교환으로 받은 access가 코드를 발급받은 바로 그 사용자를 가리켜야 한다.
        String access = exchanged.getResponse().getHeader(TokenHeaders.ACCESS).substring("Bearer ".length());
        assertThat(mypagePublicId(access)).isEqualTo(publicId);

        //같은 코드를 다시 쓰면 401. 주소창에 남은 URL로 누가 다시 시도해도 토큰이 나오면 안 된다.
        mvc.perform(post("/api/v1/auth/login/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(code)))
                .andExpect(status().isUnauthorized());
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
                 -1000L);

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

    // ---------------------------------------------------------------- @CurrentUser

    /*
    @CurrentUser 는 토큰 -> publicId -> user_id 변환을 컨트롤러 대신 해준다.
    변환이 한 곳(CurrentUserArgumentResolver)에만 있어야 팀 API가 늘어나도 구멍이 생기지 않으므로,
    "그 한 곳이 실제로 옳은 사용자를 채워주는가"를 여기서 고정한다.

    마이페이지가 돌려주는 publicId가 로그인한 사람의 것이어야 성공이다.
    (@RequestParam 으로 신원을 받던 시절이라면 파라미터로 남의 것을 넣을 수 있었다)
    */
    @Test
    @DisplayName("@CurrentUser 가 토큰이 가리키는 사용자를 채워준다")
    void CurrentUser_가_토큰의_사용자를_채운다() throws Exception {

        String accessA = signupAndLogin();
        String accessB = signupAndLogin();

        String publicIdA = mypagePublicId(accessA);
        String publicIdB = mypagePublicId(accessB);

        //서로 다른 사용자여야 이 테스트가 의미가 있다
        assertThat(publicIdA).isNotEqualTo(publicIdB);
    }

    /*
    수정 API도 같은 경로로 신원을 받는다.
    조회만 확인하고 수정을 빼두면, 나중에 수정 쪽만 파라미터로 신원을 받게 바뀌어도 아무도 모른다.
    */
    @Test
    @DisplayName("@CurrentUser 로 수정하면 내 정보만 바뀐다")
    void CurrentUser_로_수정하면_내것만_바뀐다() throws Exception {

        String accessA = signupAndLogin();
        String accessB = signupAndLogin();

        String publicIdB = mypagePublicId(accessB);
        String beforeB = mypageNickname(accessB);

        //A의 토큰으로 B의 publicId를 파라미터에 붙여 수정을 시도한다
        mvc.perform(patch(PROTECTED_API)
                        .param("userId", publicIdB)
                        .param("publicId", publicIdB)
                        .header(TokenHeaders.ACCESS, "Bearer " + accessA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"A가바꾼닉네임\"}"))
                .andExpect(status().isOk())
                //바뀐 것은 A의 것이다
                .andExpect(jsonPath("$.nickname").value("A가바꾼닉네임"));

        //B는 그대로여야 한다
        assertThat(mypageNickname(accessB)).isEqualTo(beforeB);
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

    private String mypageNickname(String access) throws Exception {

        String body = mvc.perform(get(PROTECTED_API).header(TokenHeaders.ACCESS, "Bearer " + access))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.nickname");
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
