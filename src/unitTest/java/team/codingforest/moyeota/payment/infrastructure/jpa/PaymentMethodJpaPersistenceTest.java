package team.codingforest.moyeota.payment.infrastructure.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.payment.domain.CardDetail;
import team.codingforest.moyeota.payment.domain.PaymentMethod;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({PaymentMethodJpa.class, JpaAuditingConfig.class})
class PaymentMethodJpaPersistenceTest {
    private static final Long 유저 = 1L;

    private final PaymentMethodJpa methods;

    @Autowired
    PaymentMethodJpaPersistenceTest(PaymentMethodJpa methods) {
        this.methods = methods;
    }

    @Test
    void 카드_정보가_card_테이블에_저장되고_그대로_복원된다() {
        PaymentMethod saved = methods.save(PaymentMethod.registerCard(유저, "bk-card", new CardDetail("신한카드", "1234-56**-****-7890", "VISA"), Instant.now()));

        PaymentMethod reloaded = methods.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getCard()).isEqualTo(new CardDetail("신한카드", "1234-56**-****-7890", "VISA"));
        assertThat(reloaded.getBillingKey()).isEqualTo("bk-card");
        assertThat(reloaded.getUserId()).isEqualTo(유저);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.isActive()).isTrue();
    }

    @Test
    void 삭제하면_활성_조회에서_빠지고_새_수단이_활성이_된다() {
        PaymentMethod old = methods.save(PaymentMethod.registerCard(유저, "bk-old", new CardDetail("A", "1", "B"), Instant.now()));
        old.delete(Instant.now());
        methods.save(old);

        assertThat(methods.findAllActiveByUserId(유저)).isEmpty();

        methods.save(PaymentMethod.registerCard(유저, "bk-new", new CardDetail("C", "2", "D"), Instant.now()));

        assertThat(methods.findAllActiveByUserId(유저).getFirst().getBillingKey()).isEqualTo("bk-new");
        assertThat(methods.findById(old.getId()).orElseThrow().isActive()).isFalse();
    }
}
