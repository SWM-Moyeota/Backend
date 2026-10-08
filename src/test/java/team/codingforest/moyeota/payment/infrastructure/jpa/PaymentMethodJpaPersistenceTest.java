package team.codingforest.moyeota.payment.infrastructure.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.payment.domain.CardDetail;
import team.codingforest.moyeota.payment.domain.EasyPayDetail;
import team.codingforest.moyeota.payment.domain.MobileDetail;
import team.codingforest.moyeota.payment.domain.PaymentMethod;
import team.codingforest.moyeota.payment.domain.PaymentMethodType;

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
    void 종류별_표시_정보가_각_테이블에_저장되고_복원된다() {
        PaymentMethod card = methods.save(PaymentMethod.register(유저, "bk-card", new CardDetail("신한카드", "1234-56**-****-7890", "VISA"), Instant.now()));
        PaymentMethod easy = methods.save(PaymentMethod.register(2L, "bk-easy", new EasyPayDetail("KAKAOPAY"), Instant.now()));
        PaymentMethod mobile = methods.save(PaymentMethod.register(3L, "bk-mobile", new MobileDetail("010-****-1234"), Instant.now()));

        assertThat(methods.findById(card.getId()).orElseThrow().getDetail()).isEqualTo(new CardDetail("신한카드", "1234-56**-****-7890", "VISA"));
        assertThat(methods.findById(easy.getId()).orElseThrow().getType()).isEqualTo(PaymentMethodType.EASY_PAY);
        assertThat(methods.findById(mobile.getId()).orElseThrow().getDetail()).isEqualTo(new MobileDetail("010-****-1234"));
    }

    @Test
    void 삭제하면_활성_조회에서_빠지고_새_수단이_활성이_된다() {
        PaymentMethod old = methods.save(PaymentMethod.register(유저, "bk-old", new CardDetail("A", "1", "B"), Instant.now()));
        old.delete(Instant.now());
        methods.save(old);

        assertThat(methods.findActiveByUserId(유저)).isEmpty();

        methods.save(PaymentMethod.register(유저, "bk-new", new CardDetail("C", "2", "D"), Instant.now()));

        assertThat(methods.findActiveByUserId(유저).orElseThrow().getBillingKey()).isEqualTo("bk-new");
        assertThat(methods.findById(old.getId()).orElseThrow().isActive()).isFalse();
    }
}
