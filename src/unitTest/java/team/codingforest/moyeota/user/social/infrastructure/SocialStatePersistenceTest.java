package team.codingforest.moyeota.user.social.infrastructure;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.sql.Timestamp;
import java.time.Instant;
import team.codingforest.moyeota.common.crypto.FieldCipher;
import team.codingforest.moyeota.user.social.apple.*;
import team.codingforest.moyeota.user.social.domain.*;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;
import team.codingforest.moyeota.user.common.exception.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase(replace=org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(properties="spring.datasource.url=jdbc:h2:mem:social-state;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class SocialStatePersistenceTest {
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager manager;
    JdbcTemplate jdbc; TransactionTemplate tx; SocialProofStore proofs; AppleCredentialStore credentials; FieldCipher cipher;
    @BeforeEach void 준비() {
        jdbc=new JdbcTemplate(dataSource); tx=new TransactionTemplate(manager); proofs=new SocialProofStore(jdbc);
        cipher=new FieldCipher(Base64.getEncoder().encodeToString(new byte[32])); credentials=new AppleCredentialStore(jdbc,cipher,new AppleGrantLocks(jdbc));
        jdbc.update("delete from social_proof"); jdbc.update("delete from apple_credential");
    }
    @Test void 증표는_사용자와_용도를_구분하고_실패하면_보존한다() {
        String token=proofs.issue("APPLE_LINK",7L,"nonce");
        assertThatThrownBy(() -> tx.execute(s -> proofs.consume(token,"APPLE_LOGIN",null,UserErrorCode.SOCIAL_CHALLENGE_INVALID))).isInstanceOf(UserException.class);
        assertThatThrownBy(() -> tx.execute(s -> proofs.consume(token,"APPLE_LINK",8L,UserErrorCode.SOCIAL_CHALLENGE_INVALID))).isInstanceOf(UserException.class);
        assertThat(tx.<String>execute(s -> proofs.consume(token,"APPLE_LINK",7L,UserErrorCode.SOCIAL_CHALLENGE_INVALID))).isEqualTo("nonce");
        assertThatThrownBy(() -> tx.execute(s -> proofs.consume(token,"APPLE_LINK",7L,UserErrorCode.SOCIAL_CHALLENGE_INVALID))).isInstanceOf(UserException.class);
    }
    @Test void 만료와_트랜잭션_롤백을_검증한다() {
        String token=proofs.issue("LINK_SMS",7L,"phone");
        assertThatThrownBy(() -> tx.execute(s -> { proofs.consume(token,"LINK_SMS",7L,UserErrorCode.SOCIAL_LINK_VERIFICATION_EXPIRED); throw new IllegalStateException(); })).isInstanceOf(IllegalStateException.class);
        jdbc.update("update social_proof set expires_at=?",Timestamp.from(Instant.now().minusSeconds(1)));
        assertThatThrownBy(() -> tx.execute(s -> proofs.consume(token,"LINK_SMS",7L,UserErrorCode.SOCIAL_LINK_VERIFICATION_EXPIRED))).isInstanceOf(UserException.class);
    }
    @Test void 동시에_소비하면_한_요청만_성공한다() throws Exception {
        String token=proofs.issue("APPLE_LOGIN",null,"nonce");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> consume=() -> { start.await(); try { tx.execute(s -> proofs.consume(token,"APPLE_LOGIN",null,UserErrorCode.SOCIAL_CHALLENGE_INVALID)); return true; } catch(UserException e) { return false; } };
            var a=pool.submit(consume);var b=pool.submit(consume);start.countDown();
            assertThat(List.of(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
    }
    @Test void 새_활성_동의가_있으면_만료된_대기_토큰은_원격_철회하지_않는다() {
        var old=credentials.stage(new AppleClient.Verified(new SocialProfile(SocialType.APPLE,"sub-active","email@test.com",null),"app","old-token"));
        jdbc.update("update apple_credential set expires_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(1)),old);
        var active=credentials.stage(new AppleClient.Verified(new SocialProfile(SocialType.APPLE,"sub-active","email@test.com",null),"app","new-token"));
        tx.executeWithoutResult(t -> credentials.attach(active,7L));
        var apple=mock(AppleClient.class);
        new AppleRevocationWorker(jdbc,cipher,apple,new AppleGrantLocks(jdbc),tx).run();
        verifyNoInteractions(apple);
        assertThat(jdbc.queryForObject("select count(*) from apple_credential where id=?",Integer.class,old)).isZero();
        assertThat(jdbc.queryForObject("select state from apple_credential where id=?",String.class,active)).isEqualTo("ACTIVE");
    }

    @Test void 가입_대기_중_철회된_인증으로는_활성화할_수_없다() {
        var id=credentials.stage(new AppleClient.Verified(new SocialProfile(SocialType.APPLE,"pending-revoked","email@test.com",null),"app","refresh",Instant.now().minusSeconds(5)));
        tx.executeWithoutResult(t -> { new AppleGrantLocks(jdbc).lock("pending-revoked");jdbc.update("update apple_grant_lock set revoked_at=? where subject=?",Timestamp.from(Instant.now()),"pending-revoked"); });
        assertThatThrownBy(() -> tx.executeWithoutResult(t -> credentials.attach(id,7L))).isInstanceOf(UserException.class).extracting("errorCode").isEqualTo(UserErrorCode.SOCIAL_TOKEN_INVALID);
        assertThat(jdbc.queryForObject("select state from apple_credential where id=?",String.class,id)).isEqualTo("PENDING");
    }

    @Test void 공급자_토큰을_암호화하고_만료_대기표를_재시도_철회한다() {
        var id=credentials.stage(new AppleClient.Verified(new SocialProfile(SocialType.APPLE,"sub","email@test.com",null),"app","refresh-secret"));
        String stored=jdbc.queryForObject("select token_cipher from apple_credential where id=?",String.class,id);
        assertThat(stored).startsWith("v1:").doesNotContain("refresh-secret");
        jdbc.update("update apple_credential set expires_at=?",Timestamp.from(Instant.now().minusSeconds(1)));
        var apple=mock(AppleClient.class); doThrow(new UserException(UserErrorCode.SOCIAL_UNAVAILABLE)).doNothing().when(apple).revoke("app","refresh-secret");
        var worker=new AppleRevocationWorker(jdbc,cipher,apple,new AppleGrantLocks(jdbc),tx);worker.run();
        assertThat(jdbc.queryForObject("select attempts from apple_credential where id=?",Integer.class,id)).isEqualTo(1);
        jdbc.update("update apple_credential set retry_at=?",Timestamp.from(Instant.now().minusSeconds(1)));worker.run();
        assertThat(jdbc.queryForObject("select count(*) from apple_credential",Integer.class)).isZero();
    }
}
