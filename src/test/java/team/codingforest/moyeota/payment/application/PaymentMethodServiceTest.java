package team.codingforest.moyeota.payment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.BillingKeyInfo;
import team.codingforest.moyeota.payment.domain.CardDetail;
import team.codingforest.moyeota.payment.domain.EasyPayDetail;
import team.codingforest.moyeota.payment.domain.PaymentMethodType;
import team.codingforest.moyeota.payment.dto.PaymentMethodResponse;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentMethodServiceTest {
    private static final Long 유저 = 1L;
    private static final Long 다른유저 = 2L;
    private static final CardDetail 신한카드 = new CardDetail("신한카드", "1234-56**-****-7890", "VISA");

    private FakePaymentMethods methods;
    private FakePaymentGateway gateway;
    private FakeUserAccess users;
    private PaymentMethodService service;
    private UUID 유저publicId;

    @BeforeEach
    void setUp() {
        methods = new FakePaymentMethods();
        gateway = new FakePaymentGateway();
        users = new FakeUserAccess();
        유저publicId = users.등록(유저);
        users.등록(다른유저);
        service = new PaymentMethodService(methods, gateway, users);
    }

    private void 결제사에_발급됨(String billingKey, UUID customer) {
        gateway.billingKeys.put(billingKey, new BillingKeyInfo(true, customer.toString(), 신한카드));
    }

    @Test
    void 본인_명의로_발급된_빌링키를_등록한다() {
        결제사에_발급됨("bk-1", 유저publicId);

        PaymentMethodResponse response = service.register(유저, "bk-1");

        assertThat(response.type()).isEqualTo(PaymentMethodType.CARD);
        assertThat(response.label()).isEqualTo("신한카드");
        assertThat(response.maskedNumber()).isEqualTo("1234-56**-****-7890");
        assertThat(methods.findActiveByUserId(유저)).isPresent();
        assertThat(service.findMine(유저).id()).isEqualTo(response.id());
    }

    @Test
    void 결제사에_없는_빌링키는_거부한다() {
        // 앱이 보낸 값은 믿지 않는다
        assertThatThrownBy(() -> service.register(유저, "bk-fake"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_BILLING_KEY);
    }

    @Test
    void 발급_상태가_아닌_빌링키는_거부한다() {
        gateway.billingKeys.put("bk-deleted", new BillingKeyInfo(false, 유저publicId.toString(), 신한카드));

        assertThatThrownBy(() -> service.register(유저, "bk-deleted"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_BILLING_KEY);
    }

    @Test
    void 지원하지_않는_종류의_수단은_거부한다() {
        gateway.billingKeys.put("bk-paypal", new BillingKeyInfo(true, 유저publicId.toString(), null));

        assertThatThrownBy(() -> service.register(유저, "bk-paypal"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_BILLING_KEY);
    }

    @Test
    void 다른_사람_명의_빌링키는_거부한다() {
        // 남의 빌링키 id 를 알아내 내 계정에 붙여 남의 카드로 결제하는 시나리오 차단
        결제사에_발급됨("bk-other", UUID.randomUUID());

        assertThatThrownBy(() -> service.register(유저, "bk-other"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.BILLING_KEY_OWNER_MISMATCH);
        assertThat(methods.findActiveByUserId(유저)).isEmpty();
    }

    @Test
    void 교체하면_이전_수단은_비활성화되고_결제사에서도_지운다() {
        결제사에_발급됨("bk-old", 유저publicId);
        service.register(유저, "bk-old");
        gateway.billingKeys.put("bk-new", new BillingKeyInfo(true, 유저publicId.toString(), new EasyPayDetail("KAKAOPAY")));

        PaymentMethodResponse replaced = service.register(유저, "bk-new");

        assertThat(replaced.type()).isEqualTo(PaymentMethodType.EASY_PAY);
        assertThat(methods.findActiveByUserId(유저).orElseThrow().getBillingKey()).isEqualTo("bk-new");
        assertThat(gateway.deletedBillingKeys).containsExactly("bk-old");
    }

    @Test
    void 삭제하면_활성_수단이_없어지고_조회는_404() {
        결제사에_발급됨("bk-1", 유저publicId);
        service.register(유저, "bk-1");

        service.remove(유저);

        assertThat(gateway.deletedBillingKeys).containsExactly("bk-1");
        assertThatThrownBy(() -> service.findMine(유저))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND);
    }

    @Test
    void 결제사_빌링키_삭제가_실패해도_우리_쪽_삭제는_유지된다() {
        결제사에_발급됨("bk-1", 유저publicId);
        service.register(유저, "bk-1");
        gateway.down = true;

        service.remove(유저);

        assertThat(methods.findActiveByUserId(유저)).isEmpty();
    }
}
