package team.codingforest.moyeota.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.client.PortOneClient;
import team.codingforest.moyeota.payment.dto.BillingKeyInfo;
import team.codingforest.moyeota.payment.dto.CardDetail;
import team.codingforest.moyeota.payment.dto.PaymentMethodResponse;
import team.codingforest.moyeota.payment.entity.PaymentMethod;
import team.codingforest.moyeota.payment.entity.enums.PaymentMethodType;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;
import team.codingforest.moyeota.payment.repository.PaymentMethodRepository;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 저장소는 실제 JPA(H2)를 쓰고, 결제사와 유저 조회만 가짜로 둔다 */
@DataJpaTest
@Import({PaymentMethodService.class, JpaAuditingConfig.class})
class PaymentMethodServiceTest {
    private static final Long 유저 = 1L;
    private static final Long 다른유저 = 2L;
    private static final CardDetail 신한카드 = new CardDetail("신한카드", "1234-56**-****-7890", "VISA");
    private static final CardDetail 국민카드 = new CardDetail("국민카드", "9876-54**-****-3210", "MASTER");

    @Autowired PaymentMethodRepository methods;
    @Autowired PaymentMethodService service;
    @Autowired TestEntityManager em;
    @MockitoBean PortOneClient gateway;
    @MockitoBean UserAccess users;
    private UUID 유저publicId;

    @BeforeEach
    void setUp() {
        유저publicId = 등록(유저);
        등록(다른유저);
        // 심어 두지 않은 빌링키는 결제사에 없는 키로 본다
        doThrow(new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY)).when(gateway).inspectBillingKey(anyString());
    }

    private UUID 등록(Long userId) {
        UUID publicId = UUID.randomUUID();
        when(users.findMemberSummaries(List.of(userId))).thenReturn(Map.of(userId, new MemberSummary(publicId, "닉" + userId, null, null)));
        return publicId;
    }

    private void 결제사에_발급됨(String billingKey, UUID customer, CardDetail card) {
        결제사_응답(billingKey, new BillingKeyInfo(true, customer.toString(), card));
    }

    private void 결제사_응답(String billingKey, BillingKeyInfo info) {
        doReturn(info).when(gateway).inspectBillingKey(billingKey);
    }

    private void 결제사_장애() {
        doThrow(new BusinessException(PaymentErrorCode.GATEWAY_ERROR)).when(gateway).inspectBillingKey(anyString());
    }

    @Test
    void 본인_명의로_발급된_카드_빌링키를_등록한다() {
        결제사에_발급됨("bk-1", 유저publicId, 신한카드);

        PaymentMethodResponse response = service.register(유저, "bk-1");

        assertThat(response.type()).isEqualTo(PaymentMethodType.CARD);
        assertThat(response.label()).isEqualTo("신한카드");
        assertThat(response.maskedNumber()).isEqualTo("1234-56**-****-7890");
        assertThat(response.brand()).isEqualTo("VISA");
        assertThat(methods.findDefaultByUserId(유저).orElseThrow().getBillingKey()).isEqualTo("bk-1");
    }

    @Test
    void 결제사에_없는_빌링키는_거부한다() {
        // 앱이 보낸 값은 믿지 않는다
        assertThatThrownBy(() -> service.register(유저, "bk-fake"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_BILLING_KEY);
        assertThat(methods.count()).isZero();
    }

    @Test
    void 발급_상태가_아닌_빌링키는_거부한다() {
        결제사_응답("bk-deleted", new BillingKeyInfo(false, 유저publicId.toString(), 신한카드));

        assertThatThrownBy(() -> service.register(유저, "bk-deleted"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_BILLING_KEY);
    }

    @Test
    void 카드가_아닌_수단의_빌링키는_거부한다() {
        // 간편결제·휴대폰으로 발급된 빌링키 - 지금은 카드만 받는다
        결제사_응답("bk-kakaopay", new BillingKeyInfo(true, 유저publicId.toString(), null));

        assertThatThrownBy(() -> service.register(유저, "bk-kakaopay"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.UNSUPPORTED_PAYMENT_METHOD);
    }

    @Test
    void 다른_사람_명의_빌링키는_거부한다() {
        // 남의 빌링키 id 를 알아내 내 계정에 붙여 남의 카드로 결제하는 시나리오 차단
        결제사에_발급됨("bk-other", UUID.randomUUID(), 신한카드);

        assertThatThrownBy(() -> service.register(유저, "bk-other"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.BILLING_KEY_OWNER_MISMATCH);
        assertThat(methods.findDefaultByUserId(유저)).isEmpty();
    }

    @Test
    void 결제사_장애면_등록하지_않고_GATEWAY_ERROR() {
        결제사_장애();

        assertThatThrownBy(() -> service.register(유저, "bk-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.GATEWAY_ERROR);
        assertThat(methods.count()).isZero();
    }

    @Test
    void 여러_카드를_보관하며_첫_카드만_기본으로_지정한다() {
        결제사에_발급됨("bk-old", 유저publicId, 신한카드);
        PaymentMethodResponse first = service.register(유저, "bk-old");
        결제사에_발급됨("bk-new", 유저publicId, 국민카드);
        PaymentMethodResponse second = service.register(유저, "bk-new");
        assertThat(first.isDefault()).isTrue();
        assertThat(second.isDefault()).isFalse();
        assertThat(service.list(유저)).hasSize(2);
        assertThat(methods.findAllActiveByUserId(유저)).allMatch(PaymentMethod::isActive);
        assertThat(methods.findDefaultByUserId(유저).orElseThrow().getId()).isEqualTo(first.id());
        verify(gateway, never()).deleteBillingKey(anyString());
    }

    @Test
    void 같은_빌링키를_다시_보내면_등록된_수단을_그대로_돌려준다() {
        // 앱이 응답을 못 받고 재시도한 경우 - 지웠다 다시 넣으면 billing_key unique 에 걸린다
        결제사에_발급됨("bk-1", 유저publicId, 신한카드);
        PaymentMethodResponse first = service.register(유저, "bk-1");

        PaymentMethodResponse again = service.register(유저, "bk-1");

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(methods.count()).isEqualTo(1);
        verify(gateway, never()).deleteBillingKey(anyString());
    }

    @Test
    void 기본_카드_변경은_기존_카드와_빌링키를_유지하며_재요청도_가능하다() {
        결제사에_발급됨("bk-old", 유저publicId, 신한카드);
        PaymentMethodResponse first = service.register(유저, "bk-old");
        결제사에_발급됨("bk-new", 유저publicId, 국민카드);
        PaymentMethodResponse second = service.register(유저, "bk-new");
        assertThat(service.selectDefault(유저, second.id()).isDefault()).isTrue();
        assertThat(service.selectDefault(유저, second.id()).isDefault()).isTrue();
        assertThat(service.register(유저, "bk-old").isDefault()).isFalse();
        assertThat(methods.findById(first.id()).orElseThrow().isActive()).isTrue();
        assertThat(service.list(유저).stream().filter(PaymentMethodResponse::isDefault)).hasSize(1);
        verify(gateway, never()).deleteBillingKey(anyString());
    }

    @Test
    void 유저마다_따로_등록된다() {
        결제사에_발급됨("bk-1", 유저publicId, 신한카드);
        service.register(유저, "bk-1");

        assertThat(methods.findDefaultByUserId(다른유저)).isEmpty();
    }

    @Test
    void 없는_사용자는_외부_조회보다_먼저_거부한다() {
        결제사_장애();
        assertThatThrownBy(() -> service.register(999L, "bk-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_USER_NOT_FOUND);
        assertThat(methods.count()).isZero();
    }

    @Test
    void 삭제된_카드는_재등록하거나_기본으로_선택할_수_없다() {
        결제사에_발급됨("bk-old", 유저publicId, 신한카드);
        PaymentMethodResponse first = service.register(유저, "bk-old");
        PaymentMethod deleted = methods.findById(first.id()).orElseThrow();
        deleted.delete(Instant.now());
        methods.saveAndFlush(deleted);
        assertThatThrownBy(() -> service.register(유저, "bk-old"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.selectDefault(유저, first.id()))
                .isInstanceOf(BusinessException.class);
        assertThat(service.list(유저)).isEmpty();
    }

    @Test
    void 다른_사용자_카드와_없는_카드를_기본으로_선택할_수_없다() {
        결제사에_발급됨("bk-old", 유저publicId, 신한카드);
        PaymentMethodResponse first = service.register(유저, "bk-old");
        assertThatThrownBy(() -> service.selectDefault(다른유저, first.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND);
        assertThatThrownBy(() -> service.selectDefault(유저, 999L))
                .isInstanceOf(BusinessException.class);
        assertThat(service.list(다른유저)).isEmpty();
        assertThat(methods.findDefaultByUserId(유저).orElseThrow().getId()).isEqualTo(first.id());
    }

    @Test
    void 본인_카드를_삭제하면_목록에서_빠지고_카드_표시정보가_지워진다() {
        결제사에_발급됨("bk-1", 유저publicId, 신한카드);
        PaymentMethodResponse registered = service.register(유저, "bk-1");

        PaymentMethodResponse deleted = service.deleteCard(유저, registered.id());
        em.flush();
        em.clear();

        assertThat(deleted.id()).isEqualTo(registered.id());
        assertThat(deleted.isDefault()).isFalse();
        assertThat(service.list(유저)).isEmpty();
        PaymentMethod stored = methods.findById(registered.id()).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getDeletedAt()).isNotNull();
        assertThat(stored.getBillingKey()).isEqualTo("bk-1");
        assertThat(stored.getCard().getLabel()).isNull();
        assertThat(stored.getCard().getNumber()).isNull();
        assertThat(stored.getCard().getBrand()).isNull();
    }

    @Test
    void 기본_카드를_삭제하면_기본_지정이_해제되고_남은_카드는_기본이_아니다() {
        결제사에_발급됨("bk-old", 유저publicId, 신한카드);
        PaymentMethodResponse first = service.register(유저, "bk-old");
        결제사에_발급됨("bk-new", 유저publicId, 국민카드);
        PaymentMethodResponse second = service.register(유저, "bk-new");

        service.deleteCard(유저, first.id());

        assertThat(methods.findDefaultByUserId(유저)).isEmpty();
        assertThat(service.list(유저)).extracting(PaymentMethodResponse::id).containsExactly(second.id());
        assertThat(service.list(유저)).noneMatch(PaymentMethodResponse::isDefault);
    }

    @Test
    void 다른_사용자의_카드는_삭제할_수_없다() {
        // 남의 카드 ID 를 알아내 지우는 시나리오 차단. 존재 여부를 노출하지 않도록 NOT_FOUND 로 응답한다
        결제사에_발급됨("bk-1", 유저publicId, 신한카드);
        PaymentMethodResponse first = service.register(유저, "bk-1");

        assertThatThrownBy(() -> service.deleteCard(다른유저, first.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND);
        assertThat(methods.findById(first.id()).orElseThrow().isActive()).isTrue();
        assertThat(service.list(유저)).hasSize(1);
    }

    @Test
    void 없는_카드는_삭제할_수_없다() {
        assertThatThrownBy(() -> service.deleteCard(유저, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND);
    }

    @Test
    void 이미_삭제된_카드를_다시_삭제하면_PAYMENT_METHOD_DELETED() {
        결제사에_발급됨("bk-1", 유저publicId, 신한카드);
        PaymentMethodResponse first = service.register(유저, "bk-1");
        service.deleteCard(유저, first.id());
        Instant 처음삭제시각 = methods.findById(first.id()).orElseThrow().getDeletedAt();

        assertThatThrownBy(() -> service.deleteCard(유저, first.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_METHOD_DELETED);
        assertThat(methods.findById(first.id()).orElseThrow().getDeletedAt()).isEqualTo(처음삭제시각);
    }

    @Test
    void 삭제한_카드의_빌링키는_다시_등록할_수_없고_기본으로도_선택할_수_없다() {
        결제사에_발급됨("bk-1", 유저publicId, 신한카드);
        PaymentMethodResponse first = service.register(유저, "bk-1");
        service.deleteCard(유저, first.id());

        assertThatThrownBy(() -> service.register(유저, "bk-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_METHOD_CONFLICT);
        assertThatThrownBy(() -> service.selectDefault(유저, first.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND);
        assertThat(methods.count()).isEqualTo(1);
    }

    @Test
    void 없는_사용자는_카드를_삭제할_수_없다() {
        결제사에_발급됨("bk-1", 유저publicId, 신한카드);
        PaymentMethodResponse first = service.register(유저, "bk-1");

        assertThatThrownBy(() -> service.deleteCard(999L, first.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_USER_NOT_FOUND);
        assertThat(methods.findById(first.id()).orElseThrow().isActive()).isTrue();
    }
}
