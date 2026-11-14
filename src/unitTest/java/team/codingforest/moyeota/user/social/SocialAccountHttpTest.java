package team.codingforest.moyeota.user.social;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import tools.jackson.databind.*;
import team.codingforest.moyeota.user.auth.domain.PrincipalCache;
import team.codingforest.moyeota.user.auth.application.AuthService;
import team.codingforest.moyeota.user.verification.application.SmsVerificationService;
import team.codingforest.moyeota.user.social.domain.*;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;
import team.codingforest.moyeota.user.social.apple.*;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 보안 필터·MVC·서비스·H2 트랜잭션을 연결한다. 외부 공급자 HTTP 검증은 AppleClientTest에서 별도로 검증한다. */
@SpringBootTest(properties={"spring.config.import=classpath:config/profile-images.yaml","spring.datasource.url=jdbc:h2:mem:social-http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class SocialAccountHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @MockitoBean AppleClient apple;
    @MockitoBean GoogleTokenVerifier google;
    @MockitoBean PrincipalCache cache;
    @MockitoBean SmsVerificationService sms;
    @MockitoBean SocialSignupTickets tickets;
    @MockitoBean(name="chatRedisListenerContainer") RedisMessageListenerContainer chatContainer;
    @MockitoBean(name="partySseListenerContainer") RedisMessageListenerContainer partyContainer;
    final Map<String,SocialProfile> pending=new HashMap<>();
    @BeforeEach void 준비() {
        pending.clear();
        doAnswer(i -> { pending.put(i.getArgument(0),i.getArgument(1));return null; }).when(tickets).save(anyString(),any(),any());
        when(tickets.find(anyString())).thenAnswer(i -> Optional.ofNullable(pending.get(i.getArgument(0))));
        doAnswer(i -> { pending.remove(i.getArgument(0));return null; }).when(tickets).delete(anyString());
    }
    @Test void 애플_가입_구글_연결_다른_기기_로그인_재발급_탈퇴까지_같은_회원이다() throws Exception {
        String unique=UUID.randomUUID().toString();
        JsonNode session=signup("apple-"+unique,"01012345671","연결"+System.nanoTime()%100000);
        String access=session.path("accessToken").asText();
        var owner=auth.authenticate(access);
        JsonNode methods=getJson("/api/v1/users/me/login-methods",access);
        assertThat(methods.path("hasPassword").asBoolean()).isFalse();
        assertThat(methods.path("methods").get(0).path("provider").asText()).isEqualTo("APPLE");
        when(google.verify("google")).thenReturn(new SocialProfile(SocialType.GOOGLE,"google-"+unique,"g@test.com",null));
        String verification=postJson("/api/v1/users/me/login-methods/sms/verify",Map.of("code","123456"),access,200).path("verificationToken").asText();
        postJson("/api/v1/users/me/login-methods/google",Map.of("idToken","google","verificationToken",verification),access,204);
        JsonNode android=postJson("/api/v1/auth/login/google",Map.of("idToken","google"),null,200);
        assertThat(auth.authenticate(android.path("accessToken").asText()).publicId()).isEqualTo(owner.publicId());
        assertThat(getJson("/api/v1/users/me/login-methods",access).path("methods").size()).isEqualTo(2);
        // 같은 증표 재사용은 거절되고 로그인 수단 수는 그대로다.
        assertThat(postJson("/api/v1/users/me/login-methods/google",Map.of("idToken","google","verificationToken",verification),access,401).path("code").asText()).isEqualTo("USER406");
        // 같은 공급자의 다른 계정으로 교체하지 않는다. 실패 시 증표는 남는다.
        String next=postJson("/api/v1/users/me/login-methods/sms/verify",Map.of("code","123456"),access,200).path("verificationToken").asText();
        when(google.verify("other")).thenReturn(new SocialProfile(SocialType.GOOGLE,"other-"+unique,"other@test.com",null));
        assertThat(postJson("/api/v1/users/me/login-methods/google",Map.of("idToken","other","verificationToken",next),access,409).path("code").asText()).isEqualTo("USER405");
        postJson("/api/v1/users/me/login-methods/google",Map.of("idToken","google","verificationToken",next),access,204);
        JsonNode reissue=postJson("/api/v1/auth/reissue",Map.of("refreshToken",session.path("refreshToken").asText()),null,200);
        assertThat(auth.authenticate(reissue.path("accessToken").asText()).publicId()).isEqualTo(owner.publicId());
        mvc.perform(delete("/api/v1/users/me").header("Authorization","Bearer "+access)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from social_user where user_id=?",Integer.class,owner.userId())).isZero();
        assertThat(jdbc.queryForObject("select state from apple_credential where user_id=?",String.class,owner.userId())).isEqualTo("REVOKE");
        postJson("/api/v1/auth/reissue",Map.of("refreshToken",reissue.path("refreshToken").asText()),null,401);
    }
    @Test void 계정삭제_알림은_애플만_비활성화하고_기존_세션을_무효화한다() throws Exception {
        String subject="notify-"+UUID.randomUUID();
        var session=signup(subject,"01012345672","알림"+System.nanoTime()%100000);String access=session.path("accessToken").asText();
        var owner=auth.authenticate(access);
        when(google.verify("notify-google")).thenReturn(new SocialProfile(SocialType.GOOGLE,"notify-g-"+subject,"g@test.com",null));
        String verification=postJson("/api/v1/users/me/login-methods/sms/verify",Map.of("code","123456"),access,200).path("verificationToken").asText();
        postJson("/api/v1/users/me/login-methods/google",Map.of("idToken","notify-google","verificationToken",verification),access,204);
        var stale=Jwts.claims().id("stale-"+subject).add("events",Map.of("type","consent-revoked","sub",subject,"event_time",Instant.now().minusSeconds(30).getEpochSecond())).build();
        when(apple.verifyNotification("stale")).thenReturn(stale);
        postJson("/api/v1/auth/apple/notifications",Map.of("payload","stale"),null,204);
        assertThat(auth.authenticate(access).userId()).isEqualTo(owner.userId());
        assertThat(jdbc.queryForObject("select status from social_user where user_id=? and social_type='APPLE'",String.class,owner.userId())).isEqualTo("ACTIVE");
        var future=Jwts.claims().id("future-"+subject).add("events",Map.of("type","account-deleted","sub",subject,"event_time",Instant.now().plusSeconds(120).getEpochSecond())).build();
        when(apple.verifyNotification("future")).thenReturn(future);
        postJson("/api/v1/auth/apple/notifications",Map.of("payload","future"),null,401);
        var claims=Jwts.claims().id("notification-"+subject).add("events",Map.of("type","account-deleted","sub",subject,"event_time",Instant.now().getEpochSecond())).build();
        when(apple.verifyNotification("payload")).thenReturn(claims);
        postJson("/api/v1/auth/apple/notifications",Map.of("payload","payload"),null,204);
        postJson("/api/v1/auth/apple/notifications",Map.of("payload","payload"),null,204);
        mvc.perform(get("/api/v1/users/me/login-methods").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select status from social_user where user_id=? and social_type='APPLE'",String.class,owner.userId())).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("select status from social_user where user_id=? and social_type='GOOGLE'",String.class,owner.userId())).isEqualTo("ACTIVE");
        var again=postJson("/api/v1/auth/login/google",Map.of("idToken","notify-google"),null,200);
        assertThat(auth.authenticate(again.path("accessToken").asText()).userId()).isEqualTo(owner.userId());
    }
    @Test void 밀리초_event_time과_문자열_events와_account_delete_표기_알림도_처리한다() throws Exception {
        String subject="notify-ms-"+UUID.randomUUID();
        String access=signup(subject,"01012345679","밀리"+System.nanoTime()%100000).path("accessToken").asText();
        var owner=auth.authenticate(access);
        String events=json.writeValueAsString(Map.of("type","account-delete","sub",subject,"event_time",Instant.now().toEpochMilli()));
        when(apple.verifyNotification("millis")).thenReturn(Jwts.claims().id("millis-"+subject).add("events",events).build());
        postJson("/api/v1/auth/apple/notifications",Map.of("payload","millis"),null,204);
        assertThat(jdbc.queryForObject("select status from social_user where user_id=? and social_type='APPLE'",String.class,owner.userId())).isEqualTo("REVOKED");
        mvc.perform(get("/api/v1/users/me/login-methods").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        // 밀리초로 해석해도 30초 넘게 미래인 알림은 거절한다.
        String future=json.writeValueAsString(Map.of("type","consent-revoked","sub",subject,"event_time",Instant.now().plusSeconds(120).toEpochMilli()));
        when(apple.verifyNotification("millis-future")).thenReturn(Jwts.claims().id("millis-future-"+subject).add("events",future).build());
        postJson("/api/v1/auth/apple/notifications",Map.of("payload","millis-future"),null,401);
    }
    @Test void 다른_회원의_소셜_계정과_증표를_가져와도_연결하지_못한다() throws Exception {
        String unique=UUID.randomUUID().toString();
        var first=signup("first-"+unique,"01012345673","첫째"+System.nanoTime()%100000);
        var second=signup("second-"+unique,"01012345674","둘째"+System.nanoTime()%100000);
        String a=first.path("accessToken").asText(),b=second.path("accessToken").asText();
        when(google.verify("shared")).thenReturn(new SocialProfile(SocialType.GOOGLE,"shared-"+unique,"shared@test.com",null));
        String proofA=postJson("/api/v1/users/me/login-methods/sms/verify",Map.of("code","123456"),a,200).path("verificationToken").asText();
        postJson("/api/v1/users/me/login-methods/google",Map.of("idToken","shared","verificationToken",proofA),b,401);
        postJson("/api/v1/users/me/login-methods/google",Map.of("idToken","shared","verificationToken",proofA),a,204);
        String proofB=postJson("/api/v1/users/me/login-methods/sms/verify",Map.of("code","123456"),b,200).path("verificationToken").asText();
        postJson("/api/v1/users/me/login-methods/google",Map.of("idToken","shared","verificationToken",proofB),b,409);
        assertThat(getJson("/api/v1/users/me/login-methods",b).path("methods").size()).isEqualTo(1);
    }

    @Test void 서로_다른_회원이_동시에_같은_구글_계정을_연결하면_하나만_성공한다() throws Exception {
        String unique=UUID.randomUUID().toString();
        String a=signup("race-a-"+unique,"01012345675","동시일"+System.nanoTime()%10000).path("accessToken").asText();
        String b=signup("race-b-"+unique,"01012345676","동시이"+System.nanoTime()%10000).path("accessToken").asText();
        when(google.verify("race-google")).thenReturn(new SocialProfile(SocialType.GOOGLE,"race-g-"+unique,"race@test.com",null));
        String pa=postJson("/api/v1/users/me/login-methods/sms/verify",Map.of("code","123456"),a,200).path("verificationToken").asText();
        String pb=postJson("/api/v1/users/me/login-methods/sms/verify",Map.of("code","123456"),b,200).path("verificationToken").asText();
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> { start.await(); return linkStatus(a,pa); });
            var second=pool.submit(() -> { start.await(); return linkStatus(b,pb); }); start.countDown();
            assertThat(List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,409);
        }
        assertThat(jdbc.queryForObject("select count(*) from social_user where social_type='GOOGLE' and social_id=?",Integer.class,"race-g-"+unique)).isEqualTo(1);
    }
    private int linkStatus(String access,String proof) throws Exception {
        return mvc.perform(post("/api/v1/users/me/login-methods/google").header("Authorization","Bearer "+access).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idToken","race-google","verificationToken",proof)))).andReturn().getResponse().getStatus();
    }
    @Test void 탈퇴와_동시_재발급이_경합해도_살아있는_리프레시는_남지_않는다() throws Exception {
        String unique=UUID.randomUUID().toString();
        var session=signup("delete-race-"+unique,"01012345677","탈퇴동시"+System.nanoTime()%10000);
        String access=session.path("accessToken").asText(),refresh=session.path("refreshToken").asText();
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var deletion=pool.submit(() -> { start.await();return mvc.perform(delete("/api/v1/users/me").header("Authorization","Bearer "+access)).andReturn().getResponse(); });
            var rotation=pool.submit(() -> { start.await();return mvc.perform(post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("refreshToken",refresh)))).andReturn().getResponse(); });
            start.countDown();assertThat(deletion.get(10,java.util.concurrent.TimeUnit.SECONDS).getStatus()).isEqualTo(204);
            var result=rotation.get(10,java.util.concurrent.TimeUnit.SECONDS);assertThat(result.getStatus()).isIn(200,401);
            if(result.getStatus()==200) postJson("/api/v1/auth/reissue",Map.of("refreshToken",json.readTree(result.getContentAsString()).path("refreshToken").asText()),null,401);
        }
        postJson("/api/v1/auth/reissue",Map.of("refreshToken",refresh),null,401);
    }

    private JsonNode signup(String subject,String phone,String nickname) throws Exception {
        when(apple.subject(subject)).thenReturn(subject);
        when(apple.exchange(eq(subject),eq("code"),anyString(),isNull())).thenReturn(new AppleClient.Verified(new SocialProfile(SocialType.APPLE,subject,"relay@test.com",null),"app","encrypted-token",Instant.now().minusSeconds(5)));
        String challenge=postJson("/api/v1/auth/apple/challenge",Map.of(),null,200).path("challengeToken").asText();
        var login=postJson("/api/v1/auth/login/apple",Map.of("identityToken",subject,"authorizationCode","code","challengeToken",challenge),null,200);
        assertThat(login.path("status").asText()).isEqualTo("SIGNUP_REQUIRED");
        return postJson("/api/v1/auth/social/register",Map.of("signupToken",login.path("signupToken").asText(),"verificationToken","sms","phoneNumber",phone,"nickname",nickname,"name","홍길동","birthDate","2000-01-01T00:00:00Z","gender","MALE"),null,201);
    }
    private JsonNode getJson(String path,String access) throws Exception { return json.readTree(mvc.perform(get(path).header("Authorization","Bearer "+access)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    private JsonNode postJson(String path,Object body,String access,int expected) throws Exception {
        var request=post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if(access!=null)request.header("Authorization","Bearer "+access);
        String result=mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return result.isBlank()?json.createObjectNode():json.readTree(result);
    }
}
