package team.codingforest.moyeota.user.passwordreset;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import team.codingforest.moyeota.user.passwordreset.application.*;
import team.codingforest.moyeota.user.passwordreset.domain.*;
import team.codingforest.moyeota.user.passwordreset.dto.*;
import team.codingforest.moyeota.user.passwordreset.infrastructure.*;
import team.codingforest.moyeota.user.auth.application.AuthService;
import team.codingforest.moyeota.user.auth.domain.RefreshTokens;
import team.codingforest.moyeota.user.auth.dto.UserLoginCommand;
import team.codingforest.moyeota.user.auth.infrastructure.JwtProvider;
import team.codingforest.moyeota.user.common.crypto.FieldHasher;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.local.domain.*;
import team.codingforest.moyeota.user.profile.domain.*;
import team.codingforest.moyeota.user.users.domain.*;
import team.codingforest.moyeota.user.users.domain.enums.*;
import team.codingforest.moyeota.user.verification.domain.SmsSender;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 로컬 PostgreSQL의 임시 스키마와 Redis DB 15만 사용한다. 실제 문자는 보내지 않는다. */
@SpringBootTest(properties={
        "spring.profiles.active=dev", "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create", "spring.config.import=classpath:config/profile-images.yaml",
        "spring.datasource.username=moyeota", "spring.datasource.password=moyeota",
        "spring.data.redis.host=localhost", "spring.data.redis.port=6379", "spring.data.redis.database=15",
        "spring.data.redis.ssl.enabled=false", "moyeota.password-reset.worker-enabled=false",
        "moyeota.sms.provider=log", "moyeota.sms.fixed-code=", "moyeota.sms.review.phones=", "moyeota.sms.review.code=",
        "moyeota.crypto.hash-key=reset-integration-only",
        "moyeota.crypto.field-key=XgIsaebhS+OFGOUd4l16AnNPZgXIydBInUv5yyQvmMY=",
        "jwt.secret=deXiPUTSbvL2Qg673qn22K4S6fjf6VSBMLfW54E16DrUPty9aapfs/PGqoO+NdP9"
})
@org.springframework.test.annotation.DirtiesContext
@org.springframework.test.context.TestExecutionListeners(
        listeners=PasswordResetIntegrationTest.SchemaCleanup.class,
        mergeMode=org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class PasswordResetIntegrationTest {
    static final String SCHEMA = "reset_test_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        try (var c = java.sql.DriverManager.getConnection("jdbc:postgresql://localhost:5432/moyeota", "moyeota", "moyeota");
             var statement = c.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        } catch (java.sql.SQLException e) { throw new IllegalStateException("테스트 스키마 생성 실패", e); }
        r.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/moyeota?currentSchema="+SCHEMA+",public");
        r.add("spring.flyway.schemas", () -> SCHEMA);
        r.add("spring.flyway.default-schema", () -> SCHEMA);
        r.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }
    @Autowired PasswordResetSendService sends;
    @Autowired PasswordResetVerifyService verifies;
    @Autowired PasswordResetGrantIssuer issuer;
    @Autowired PasswordResetConfirmService confirms;
    @Autowired ResetRequests requests;
    @Autowired ResetAccounts accounts;
    @Autowired PasswordResetSecrets secrets;
    @Autowired Users users;
    @Autowired LocalUsers locals;
    @Autowired UserProfiles profiles;
    @Autowired PasswordHasher hasher;
    @Autowired AuthService auth;
    @Autowired JwtProvider jwt;
    @Autowired FieldHasher fieldHasher;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired PlatformTransactionManager tx;
    @MockitoBean SmsSender sms;
    @MockitoSpyBean RefreshTokens refresh;
    final List<String> requestIds = new ArrayList<>();
    final List<String> ownedKeys = new ArrayList<>();
    Long userId;
    String loginId;
    String phone;
    String phoneHash;
    static final String OLD = "OldPassw0rd!";
    static final String NEW = "NewPassw0rd!";

    @BeforeEach void fixture() {
        long n = ThreadLocalRandom.current().nextLong(100_000_000L);
        phone = "010"+String.format("%08d",n);
        loginId = "reset"+n;
        User user = users.save(User.from(UUID.randomUUID(),LoginType.LOCAL));
        userId=user.getId();
        locals.register(userId,loginId,hasher.hash(OLD));
        profiles.save(UserProfile.of(userId,"재설정테스트",Instant.parse("2000-01-01T00:00:00Z"),phone,Gender.MALE,"reset@example.com"));
        phoneHash=fieldHasher.hash(phone);
        ownedKeys.add("sms:cooldown:"+phoneHash);
        ownedKeys.add("sms:daily:"+phoneHash);
        ownedKeys.add("password-reset:account:"+fieldHasher.hash(loginId));
        ownedKeys.add("password-reset:latest:"+fieldHasher.hash(loginId+":"+phoneHash));
    }
    @AfterEach void cleanup() {
        requestIds.forEach(id -> { redis.delete("password-reset:challenge:"+id); redis.opsForZSet().remove("password-reset:queue",id); });
        redis.delete(ownedKeys);
        reset(refresh);
    }
    // DirtiesContext가 연결·스케줄러를 종료한 다음, 이번 테스트 스키마만 정리한다.
    public static class SchemaCleanup extends org.springframework.test.context.support.AbstractTestExecutionListener {
        @Override public int getOrder() { return 1000; }
        @Override public void afterTestClass(org.springframework.test.context.TestContext testContext) throws Exception {
            try (var c = java.sql.DriverManager.getConnection("jdbc:postgresql://localhost:5432/moyeota", "moyeota", "moyeota");
                 var statement = c.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }
    @Test void V36_migration은_허가증테이블과_계정세대를_추가한다() throws Exception {
        String schema = SCHEMA + "_migration";
        try (var c=java.sql.DriverManager.getConnection("jdbc:postgresql://localhost:5432/moyeota", "moyeota", "moyeota");
             var statement=c.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                statement.execute("SET search_path TO " + schema);
                statement.execute("CREATE TABLE local_user (id bigint PRIMARY KEY)");
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,
                        new org.springframework.core.io.ClassPathResource("db/migration/V36__password_reset.sql"));
                statement.execute("INSERT INTO local_user(id) VALUES (1)");
                try(var rows=statement.executeQuery("SELECT password_reset_generation FROM local_user WHERE id=1")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero();
                }
                statement.execute("INSERT INTO password_reset_grant VALUES ('00000000-0000-0000-0000-000000000001','hash',1,'phone',0,now(),null,now())");
                statement.execute("DELETE FROM local_user WHERE id=1");
                try(var rows=statement.executeQuery("SELECT count(*) FROM password_reset_grant")) {
                    rows.next(); assertThat(rows.getLong(1)).isZero();
                }
            } finally { statement.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }
    String grant() { return issuer.issue(new VerifiedResetProof(userId,phoneHash,0)).resetToken(); }
    PasswordResetConfirmRequest confirmInput(String token) { return new PasswordResetConfirmRequest(token,NEW,NEW); }
    String send() {
        String id=sends.send(new PasswordResetSendRequest(loginId,phone)).requestId();
        requestIds.add(id);
        return id;
    }
    String ready() {
        String id=send();
        var job=requests.claimNext().orElseThrow();
        assertThat(job.requestId()).isEqualTo(id);
        assertThat(requests.prepareCode(id,new VerifiedResetProof(userId,phoneHash,0),secrets.codeDigest(id,"012345"))).isTrue();
        requests.markSent(id);
        return id;
    }

    @Test void 발송부터_변경과_재로그인까지_실제저장소로_연결된다() {
        var oldLogin=auth.login(new UserLoginCommand(loginId,OLD));
        String id=send();
        new PasswordResetSmsWorker(requests,accounts,secrets,sms).handle(requests.claimNext().orElseThrow());
        var text=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(sms).send(eq(phone),text.capture());
        var matcher=Pattern.compile("\\[(\\d{6})]").matcher(text.getValue());
        assertThat(matcher.find()).isTrue();
        String token=verifies.verify(new PasswordResetVerifyRequest(id,matcher.group(1))).resetToken();
        confirms.confirm(confirmInput(token));
        assertThat(auth.login(new UserLoginCommand(loginId,NEW))).isNotNull();
        assertThatThrownBy(() -> auth.login(new UserLoginCommand(loginId,OLD))).isInstanceOf(UserException.class);
        assertThatThrownBy(() -> auth.reissue(oldLogin.refreshToken())).isInstanceOf(UserException.class);
        assertThatThrownBy(() -> confirms.confirm(confirmInput(token))).isInstanceOf(UserException.class);
    }
    @Test void 불일치정보는_동일접수응답이고_문자를_보내지않는다() {
        var result=sends.send(new PasswordResetSendRequest(loginId,"01000000000"));
        requestIds.add(result.requestId());
        String mismatch=fieldHasher.hash("01000000000");
        ownedKeys.addAll(List.of("sms:cooldown:"+mismatch,"sms:daily:"+mismatch,
                "password-reset:latest:"+fieldHasher.hash(loginId+":"+mismatch)));
        new PasswordResetSmsWorker(requests,accounts,secrets,sms).handle(requests.claimNext().orElseThrow());
        assertThat(result.expiresIn()).isEqualTo(180);
        verifyNoInteractions(sms);
        assertThatThrownBy(() -> verifies.verify(new PasswordResetVerifyRequest(result.requestId(),"123456"))).isInstanceOf(UserException.class);
    }
    @Test void 같은코드_동시검증은_한번만_성공한다() throws Exception {
        String id=ready();
        var results=race(() -> verifies.verify(new PasswordResetVerifyRequest(id,"012345")),
                () -> verifies.verify(new PasswordResetVerifyRequest(id,"012345")));
        assertThat(results).containsExactlyInAnyOrder(true,false);
        assertThat(jdbc.queryForObject("select count(*) from password_reset_grant where user_id=?",Integer.class,userId)).isEqualTo(1);
    }
    @Test void 같은토큰_동시사용은_한번만_성공한다() throws Exception {
        String token=grant();
        assertThat(race(() -> { confirms.confirm(confirmInput(token)); return null; },
                () -> { confirms.confirm(confirmInput(token)); return null; })).containsExactlyInAnyOrder(true,false);
        assertThat(generation()).isEqualTo(1);
    }
    @Test void 서로다른토큰도_같은회원이면_한번만_성공한다() throws Exception {
        String a=grant(), b=grant();
        assertThat(race(() -> { confirms.confirm(confirmInput(a)); return null; },
                () -> { confirms.confirm(confirmInput(b)); return null; })).containsExactlyInAnyOrder(true,false);
        assertThat(generation()).isEqualTo(1);
    }
    @Test void refresh취소중_실패하면_비밀번호와_권한도_롤백된다() {
        auth.login(new UserLoginCommand(loginId,OLD));
        String token=grant();
        doThrow(new IllegalStateException("강제 롤백")).when(refresh).cancelAllByUserId(eq(userId),any());
        assertThatThrownBy(() -> confirms.confirm(confirmInput(token)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class).hasRootCauseMessage("강제 롤백");
        assertThat(generation()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id=? and cancelled_at is null",Integer.class,userId)).isEqualTo(1);
        assertThat(hasher.matches(OLD,locals.findByUserId(userId).orElseThrow().getPassword())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from password_reset_grant where user_id=? and consumed_at is null",Integer.class,userId)).isEqualTo(1);
        reset(refresh);
        confirms.confirm(confirmInput(token));
        assertThat(generation()).isEqualTo(1);
    }
    @Test void 만료된코드와_허가증은_거절한다() {
        String id=ready();
        redis.expire("password-reset:challenge:"+id,Duration.ZERO);
        assertThatThrownBy(() -> verifies.verify(new PasswordResetVerifyRequest(id,"012345"))).isInstanceOf(UserException.class);
        String token=grant();
        jdbc.update("update password_reset_grant set expires_at=now()-interval '1 second' where user_id=?",userId);
        assertThatThrownBy(() -> confirms.confirm(confirmInput(token))).isInstanceOf(UserException.class);
    }
    @Test void 다섯번_실패하면_맞는코드도_거절한다() {
        String id=ready();
        for(int i=0;i<5;i++) assertThatThrownBy(() -> verifies.verify(new PasswordResetVerifyRequest(id,"999999"))).isInstanceOf(UserException.class);
        assertThatThrownBy(() -> verifies.verify(new PasswordResetVerifyRequest(id,"012345"))).isInstanceOf(ResetRateLimited.class);
    }
    @Test void 재발송하면_이전코드와_지연작업은_거절한다() {
        String old=ready();
        redis.delete("sms:cooldown:"+phoneHash); // 60초 경과를 재현
        String current=send();
        assertThatThrownBy(() -> verifies.verify(new PasswordResetVerifyRequest(old,"012345"))).isInstanceOf(UserException.class);
        assertThat(requests.prepareCode(old,new VerifiedResetProof(userId,phoneHash,0),"digest")).isFalse();
        requests.markSent(old);
        assertThat(requests.claimNext().orElseThrow().requestId()).isEqualTo(current);
    }
    @Test void 중단된_발송작업은_재발송하지않고_무효화한다() {
        String id=send();
        requests.claimNext().orElseThrow();
        redis.opsForZSet().add("password-reset:queue",id,0); // 점유 시간 경과
        assertThat(requests.claimNext()).isEmpty();
        assertThat(redis.opsForHash().get("password-reset:challenge:"+id,"state")).isEqualTo("INVALID");
    }
    @Test void 변경후_이전세대_코드로_새허가증을_발급할수없다() {
        String id=ready();
        confirms.confirm(confirmInput(grant()));
        assertThatThrownBy(() -> verifies.verify(new PasswordResetVerifyRequest(id,"012345"))).isInstanceOf(UserException.class);
    }
    @Test void 토큰재발급과_재설정이_경쟁해도_기존refresh가_남지않는다() throws Exception {
        var session=auth.login(new UserLoginCommand(loginId,OLD));
        String token=grant();
        race(() -> auth.reissue(session.refreshToken()), () -> {confirms.confirm(confirmInput(token));return null;});
        assertThat(generation()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id=? and cancelled_at is null",Integer.class,userId)).isZero();
    }
    @Test void 이전비밀번호_로그인과_재설정이_경쟁해도_새refresh가_남지않는다() throws Exception {
        String token=grant();
        race(() -> auth.login(new UserLoginCommand(loginId,OLD)), () -> {confirms.confirm(confirmInput(token));return null;});
        assertThat(generation()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id=? and cancelled_at is null",Integer.class,userId)).isZero();
    }
    @Test void 번호변경은_기존허가증을_무효화한다() {
        String token=grant();
        var profile=profiles.findByUserId(userId).orElseThrow();
        profiles.save(UserProfile.of(userId,profile.getName(),profile.getBirthDate(),"01099998888",profile.getGender(),profile.getEmail()));
        assertThat(generation()).isEqualTo(1);
        assertThatThrownBy(() -> confirms.confirm(confirmInput(token))).isInstanceOf(UserException.class);
    }
    @Test void 탈퇴한_계정의_허가증은_사용할수없다() {
        String token=grant();
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            locals.findByUserIdForUpdate(userId);
            jdbc.update("update users set deleted_at=now() where id=?",userId);
        });
        assertThatThrownBy(() -> confirms.confirm(confirmInput(token))).isInstanceOf(UserException.class);
    }
    @Test void 기존SMS와_발송제한을_공유한다() {
        redis.opsForValue().set("sms:cooldown:"+phoneHash,"1",Duration.ofSeconds(60));
        assertThatThrownBy(this::send).isInstanceOf(ResetRateLimited.class);
        redis.delete("sms:cooldown:"+phoneHash);
        redis.opsForValue().set("sms:daily:"+phoneHash,"5",Duration.ofHours(24));
        assertThatThrownBy(this::send).isInstanceOf(ResetRateLimited.class);
    }
    @Test void 가입용Redis코드를_재설정인증으로_사용할수없다() {
        String id=send();
        ownedKeys.add("sms:code:"+phoneHash);
        redis.opsForValue().set("sms:code:"+phoneHash,"012345",Duration.ofSeconds(180));
        assertThatThrownBy(() -> verifies.verify(new PasswordResetVerifyRequest(id,"012345"))).isInstanceOf(UserException.class);
    }
    int generation() { return jdbc.queryForObject("select password_reset_generation from local_user where id=?",Integer.class,userId); }
    List<Boolean> race(Callable<?> first, Callable<?> second) throws Exception {
        try (var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            List<Future<Boolean>> futures=new ArrayList<>();
            for(var work:List.of(first,second)) futures.add(pool.submit(() -> {
                start.await();
                try { work.call(); return true; } catch(UserException e) { return false; }
            }));
            start.countDown();
            return List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS));
        }
    }
}
