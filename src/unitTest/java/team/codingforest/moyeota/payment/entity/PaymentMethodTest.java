package team.codingforest.moyeota.payment.entity;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.dto.CardDetail;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentMethodTest {
    private static final CardDetail 신한카드 = new CardDetail("신한카드", "1234-56**-****-7890", "VISA");

    @Test
    void 카드를_등록하면_활성_상태다() {
        PaymentMethod method = PaymentMethod.registerCard(1L, "bk-1", 신한카드);

        assertThat(method.isActive()).isTrue();
        assertThat(method.getType()).isEqualTo(PaymentMethodType.CARD);
        assertThat(method.hasBillingKey("bk-1")).isTrue();
        assertThat(method.getCard().getNumber()).isEqualTo("1234-56**-****-7890");
    }

    @Test
    void 삭제하면_비활성이_되고_처음_지운_시각을_유지한다() {
        PaymentMethod method = PaymentMethod.registerCard(1L, "bk-1", 신한카드);
        Instant 처음 = Instant.parse("2026-10-14T00:00:00Z");

        method.delete(처음);
        method.delete(처음.plusSeconds(60));

        assertThat(method.isActive()).isFalse();
        assertThat(method.getDeletedAt()).isEqualTo(처음);
    }

    @Test
    void 빌링키나_카드_정보가_없으면_만들_수_없다() {
        // 빌링키 없는 수단은 청구에 쓸 수 없고, 카드 정보 없는 수단은 화면에 보여줄 게 없다
        assertThatThrownBy(() -> PaymentMethod.registerCard(1L, " ", 신한카드))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.BILLING_KEY_REQUIRED);
        assertThatThrownBy(() -> PaymentMethod.registerCard(1L, "bk-1", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.CARD_INFO_REQUIRED);
    }

    @Test
    void 카드명이_없으면_브랜드를_표시한다() {
        PaymentMethod 이름없는카드 = PaymentMethod.registerCard(1L, "bk-1", new CardDetail(null, "1234-56**-****-7890", "VISA"));
        assertThat(이름없는카드.getCard().getLabel()).isEqualTo("VISA");
        assertThat(PaymentMethod.registerCard(1L, "bk-2", 신한카드).getCard().getLabel()).isEqualTo("신한카드");
    }

    @Test
    void 삭제된_카드는_기본이_될_수_없고_삭제하면_기본_지정도_해제된다() {
        PaymentMethod method = PaymentMethod.registerCard(1L, "bk-1", 신한카드);
        method.selectAsDefault();
        assertThat(method.isDefaultMethod()).isTrue();
        method.delete(Instant.now());
        assertThat(method.isDefaultMethod()).isFalse();
        assertThatThrownBy(method::selectAsDefault)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_METHOD_DELETED);
    }
}
