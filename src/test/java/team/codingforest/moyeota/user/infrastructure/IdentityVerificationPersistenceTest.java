package team.codingforest.moyeota.user.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.user.application.*;
import team.codingforest.moyeota.user.domain.*;
import team.codingforest.moyeota.user.domain.enums.LoginType;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static team.codingforest.moyeota.user.domain.exception.IdentityErrorCode.*;

@DataJpaTest(showSql = false, properties = {
        "portone.identity.store-id=store", "portone.identity.channel-key=channel", "portone.api.secret=test-secret",
        "portone.identity.di-hash-key=01234567890123456789012345678901"})
@Import({IdentityVerificationJpa.class, IdentityVerificationService.class, IdentitySettings.class,
        HmacIdentityHasher.class, JpaAuditingConfig.class, IdentityVerificationPersistenceTest.TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdentityVerificationPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");
    @TestConfiguration
    static class TimeConfig {
        @Bean Clock identityClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
    @Autowired IdentityVerificationService service;
    @Autowired IdentityVerificationJpa store;
    @Autowired UserRepository users;
    @Autowired IdentityRequestRepository requests;
    @Autowired VerifiedIdentityRepository identities;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean IdentityProvider provider;

    @BeforeEach
    void clean() {
        identities.deleteAll(); requests.deleteAll();
    }

    private Long user() {
        return users.saveAndFlush(UserEntity.from(User.from(UUID.randomUUID(), LoginType.LOCAL))).getId();
    }
    private IdentityProvider.Result verified(IdentityRequest request, String di) {
        return new IdentityProvider.Result(request.id(), "VERIFIED", "V2", "channel", "LIVE", NOW, di);
    }

    @Test
    void 타인_요청은_조회할_수_없고_인증_권한도_얻지_못한다() {
        Long owner = user(), attacker = user();
        var request = service.start(owner);
        assertThatThrownBy(() -> service.complete(attacker, request.id())).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(REQUEST_NOT_FOUND);
        assertThat(service.status(attacker).verified()).isFalse();
        verifyNoInteractions(provider);
    }

    @Test
    void 동시_시작은_한_요청으로_합쳐진다() throws Exception {
        Long owner = user();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return service.start(owner); });
            var b = pool.submit(() -> { gate.await(); return service.start(owner); });
            gate.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS).id()).isEqualTo(b.get(10, TimeUnit.SECONDS).id());
        }
        assertThat(requests.count()).isEqualTo(1);
    }

    @Test
    void 동시_완료는_한번만_저장하고_같은_결과를_반환한다() throws Exception {
        Long owner = user();
        var request = service.start(owner);
        var barrier = new CyclicBarrier(2);
        when(provider.lookup(any())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            barrier.await(5, TimeUnit.SECONDS);
            return verified(i.getArgument(0), "same-di");
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> service.complete(owner, request.id()));
            var b = pool.submit(() -> service.complete(owner, request.id()));
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
        }
        assertThat(identities.count()).isEqualTo(1);
        service.requireVerified(owner);
        assertThat(service.complete(owner, request.id()).verified()).isTrue();
        verify(provider, times(2)).lookup(any());
    }

    @Test
    void 동일인이_두_계정에서_동시에_인증해도_하나만_연결된다() throws Exception {
        Long a = user(), b = user();
        var ar = service.start(a); var br = service.start(b);
        var barrier = new CyclicBarrier(2);
        when(provider.lookup(any())).thenAnswer(i -> {
            barrier.await(5, TimeUnit.SECONDS);
            return verified(i.getArgument(0), "same-person");
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> completeOrCode(a, ar.id()));
            var second = pool.submit(() -> completeOrCode(b, br.id()));
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("성공", IDENTITY_IN_USE.getCode());
        }
        assertThat(identities.count()).isEqualTo(1);
    }

    private String completeOrCode(Long userId, String id) {
        try { service.complete(userId, id); return "성공"; }
        catch (BusinessException e) { return e.getErrorCode().getCode(); }
    }

    @Test
    void DB_저장_롤백_후에도_같은_인증_요청으로_복구한다() {
        Long owner = user();
        var request = service.start(owner);
        var tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            store.complete(request, "discarded-hash", NOW, NOW);
            throw new IllegalStateException("DB 반영 후 장애 재현");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(service.status(owner).verified()).isFalse();
        when(provider.lookup(request)).thenReturn(verified(request, "di"));
        assertThat(service.complete(owner, request.id()).verified()).isTrue();
        assertThat(identities.count()).isEqualTo(1);
    }

    @Test
    void 새_요청을_만들어도_이전_요청의_유효한_인증은_복구할_수_있다() {
        Long owner = user();
        var old = store.start(owner, "store", "channel", NOW.minusSeconds(120), Duration.ofSeconds(60));
        service.start(owner);
        when(provider.lookup(old)).thenReturn(new IdentityProvider.Result(old.id(), "VERIFIED", "V2",
                "channel", "LIVE", NOW.minusSeconds(100), "di"));
        assertThat(service.complete(owner, old.id()).verified()).isTrue();
    }
}
