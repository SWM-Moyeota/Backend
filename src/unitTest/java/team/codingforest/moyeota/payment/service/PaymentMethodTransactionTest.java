package team.codingforest.moyeota.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.payment.client.PortOneClient;
import team.codingforest.moyeota.payment.dto.BillingKeyInfo;
import team.codingforest.moyeota.payment.dto.CardDetail;
import team.codingforest.moyeota.payment.event.PaymentMethodReplacementListener;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({PaymentMethodService.class, PaymentMethodReplacementListener.class,
        JpaAuditingConfig.class, PaymentMethodTransactionTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentMethodTransactionTest {
    @Autowired PaymentMethodService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean PortOneClient gateway;
    @MockitoBean UserAccess users;

    @TestConfiguration
    static class Configuration {
        // 커밋 시점을 결정적으로 검사한다. 운영에서는 비동기 실행기를 사용한다.
        @Bean Executor taskExecutor() { return Runnable::run; }
    }

    @BeforeEach
    void setUp() {
        jdbc.update("delete from card");
        jdbc.update("delete from payment_method");
        UUID publicId = UUID.randomUUID();
        when(users.findMemberSummaries(List.of(7L)))
                .thenReturn(Map.of(7L, new MemberSummary(publicId, "사용자", null, null)));
        BillingKeyInfo info = new BillingKeyInfo(true, publicId.toString(),
                new CardDetail("신한카드", "************1234", "VISA"));
        when(gateway.inspectBillingKey(anyString())).thenReturn(info);
    }

    @Test
    void 두_테이블은_공유_PK로_저장된다() {
        Long id = service.register(7L, "bk-first").id();
        assertThat(jdbc.queryForObject("select count(*) from payment_method p join card c " +
                "on p.payment_method_id = c.payment_method_id where p.payment_method_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void 여러_카드_저장과_기본_선택은_기존_빌링키를_삭제하지_않는다() {
        Long first = service.register(7L, "bk-old").id();
        Long second = service.register(7L, "bk-new").id();
        assertThat(service.list(7L)).hasSize(2);
        service.selectDefault(7L, second);
        assertThat(jdbc.queryForObject("select count(*) from payment_method where deleted_at is null", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select payment_method_id from payment_method where is_default = true", Long.class)).isEqualTo(second);
        service.selectDefault(7L, first);
        verify(gateway, never()).deleteBillingKey(anyString());
    }

    @Test
    void 기본_카드_변경이_롤백되면_기존_기본_카드를_유지한다() {
        Long first = service.register(7L, "bk-old").id();
        Long second = service.register(7L, "bk-new").id();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            service.selectDefault(7L, second);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select payment_method_id from payment_method where is_default = true", Long.class)).isEqualTo(first);
    }

    @Test
    void 등록_후_트랜잭션이_롤백되면_옛카드가_유지되고_외부삭제도_없다() {
        service.register(7L, "bk-old");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            service.register(7L, "bk-new");
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select billing_key from payment_method where deleted_at is null", String.class))
                .isEqualTo("bk-old");
        assertThat(jdbc.queryForObject("select count(*) from card", Integer.class)).isEqualTo(1);
        verify(gateway, never()).deleteBillingKey(anyString());
    }

    @Test
    void 카드상세_저장이_실패하면_부모결제수단도_롤백된다() {
        UUID publicId = users.findMemberSummaries(List.of(7L)).get(7L).publicId();
        when(gateway.inspectBillingKey("bk-bad")).thenReturn(new BillingKeyInfo(true, publicId.toString(),
                new CardDetail("x".repeat(256), "************1234", "VISA")));
        assertThatThrownBy(() -> service.register(7L, "bk-bad")).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select count(*) from payment_method", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from card", Integer.class)).isZero();
        verify(gateway, never()).deleteBillingKey(anyString());
    }
    @Test
    void 동시에_다른_카드를_기본으로_선택해도_기본은_하나다() throws Exception {
        service.register(7L, "bk-first");
        Long second = service.register(7L, "bk-second").id();
        Long third = service.register(7L, "bk-third").id();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return service.selectDefault(7L, second); });
            var b = executor.submit(() -> { start.await(); return service.selectDefault(7L, third); });
            start.countDown();
            a.get(10, java.util.concurrent.TimeUnit.SECONDS);
            b.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from payment_method where is_default = true", Integer.class)).isEqualTo(1);
        assertThat(service.list(7L)).hasSize(3);
        verify(gateway, never()).deleteBillingKey(anyString());
    }
}
