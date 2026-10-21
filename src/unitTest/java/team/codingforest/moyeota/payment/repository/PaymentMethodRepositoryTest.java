package team.codingforest.moyeota.payment.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.payment.dto.CardDetail;
import team.codingforest.moyeota.payment.entity.PaymentMethod;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class PaymentMethodRepositoryTest {
    private static final Long 유저 = 1L;

    @Autowired PaymentMethodRepository repository;
    @Autowired TestEntityManager em;

    @Test
    void 카드_정보가_card_테이블에_저장되고_그대로_복원된다() {
        PaymentMethod saved = repository.saveAndFlush(PaymentMethod.registerCard(유저, "bk-card", new CardDetail("신한카드", "1234-56**-****-7890", "VISA")));
        em.clear();

        PaymentMethod reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getCard().getName()).isEqualTo("신한카드");
        assertThat(reloaded.getCard().getNumber()).isEqualTo("1234-56**-****-7890");
        assertThat(reloaded.getCard().getBrand()).isEqualTo("VISA");
        assertThat(reloaded.getBillingKey()).isEqualTo("bk-card");
        assertThat(reloaded.getUserId()).isEqualTo(유저);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.isActive()).isTrue();
    }

    @Test
    void 삭제하면_활성_조회에서_빠지고_새_수단이_활성이_된다() {
        PaymentMethod old = repository.save(PaymentMethod.registerCard(유저, "bk-old", new CardDetail("A", "1", "B")));
        old.delete(Instant.now());
        repository.save(old);

        assertThat(repository.findAllActiveByUserId(유저)).isEmpty();

        repository.save(PaymentMethod.registerCard(유저, "bk-new", new CardDetail("C", "2", "D")));

        assertThat(repository.findAllActiveByUserId(유저).getFirst().getBillingKey()).isEqualTo("bk-new");
        assertThat(repository.findById(old.getId()).orElseThrow().isActive()).isFalse();
    }
}
