package team.codingforest.moyeota.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;
import team.codingforest.moyeota.payment.entity.Payment;
import team.codingforest.moyeota.payment.entity.enums.PaymentStatus;
import team.codingforest.moyeota.payment.dto.portone.PortOnePaymentResponse;
import team.codingforest.moyeota.payment.dto.portone.BillingKeyPaymentSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartySummary;
import team.codingforest.moyeota.payment.client.PortOneClient;
import team.codingforest.moyeota.payment.entity.PaymentGroup;
import team.codingforest.moyeota.payment.entity.PaymentMethod;
import team.codingforest.moyeota.payment.entity.enums.PaymentGroupStatus;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;
import team.codingforest.moyeota.payment.repository.PaymentGroupRepository;
import team.codingforest.moyeota.payment.repository.PaymentLogRepository;
import team.codingforest.moyeota.payment.repository.PaymentMethodRepository;
import team.codingforest.moyeota.payment.repository.PaymentRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 준비 단계의 분기만 검증한다. 실제 DB 커밋·결제 성공을 검증하는 테스트는 아니다. */
class PaymentPreparationTest {
    private Payment savedPayment;
    private final PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
    private final PaymentGroupRepository groups = mock(PaymentGroupRepository.class);
    private final PortOneClient gateway = mock(PortOneClient.class);
    private final PartyAccess parties = mock(PartyAccess.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final PaymentService service = new PaymentService(
            payments, gateway, parties, methods, groups, mock(PaymentLogRepository.class), transactions);

    @BeforeEach
    void 준비() {
        when(groups.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(payments.save(any())).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            savedPayment = saved;
            ReflectionTestUtils.setField(saved, "id", 123L);
            when(payments.findById(123L)).thenReturn(Optional.of(saved));
            return saved;
        });
        when(parties.findSummary(10L)).thenReturn(Optional.of(new PartySummary(
                10L, 0, 0, 0, 0, "출발", "도착", 3, 12000, 10, null)));
        when(parties.hasMemberOnParty(10L, 1L)).thenReturn(true);
    }

    @Test
    void 기본_결제수단이_없으면_그룹을_만들지_않는다() {
        when(methods.findDefaultByUserId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.prePay(1L, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND));
        verifyNoInteractions(groups, gateway);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void 빌링키가_없으면_그룹을_만들지_않는다(String billingKey) {
        결제수단(billingKey);
        assertThatThrownBy(() -> service.prePay(1L, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.BILLING_KEY_REQUIRED));
        verifyNoInteractions(groups, gateway);
    }

    @Test
    void 기존_그룹이_있으면_새로_저장하지_않는다() {
        결제수단("billing-key");
        when(groups.findByMatchId(10L)).thenReturn(Optional.of(PaymentGroup.create(10L, null, 3)));
        assertThat(service.prePay(1L, 10L).status()).isEqualTo(PaymentStatus.PAID);
        verify(groups).findByMatchId(10L);
        verify(groups, never()).save(any());
        verify(gateway).payWithBillingKey(eq(savedPayment.getId().toString()), eq("billing-key"), eq(12000L));
    }

    @Test
    void 그룹이_없으면_현재_인원과_미배정_기사로_생성한다() {
        결제수단("billing-key");
        when(groups.findByMatchId(10L)).thenReturn(Optional.empty());
        assertThat(service.prePay(1L, 10L).status()).isEqualTo(PaymentStatus.PAID);
        var captor = org.mockito.ArgumentCaptor.forClass(PaymentGroup.class);
        verify(groups).save(captor.capture());
        PaymentGroup group = captor.getValue();
        assertThat(group.getMatchId()).isEqualTo(10L);
        assertThat(group.getTaxiDriverId()).isNull();
        assertThat(group.getPassengerCount()).isEqualTo(3);
        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.PENDING);
        verify(gateway).payWithBillingKey(eq(savedPayment.getId().toString()), eq("billing-key"), eq(12000L));
    }

    @Test
    void 시작_기록_커밋_후_외부_호출하고_결과를_별도_커밋한다() {
        결제수단("billing-key");
        var result = service.prePay(1L, 10L);
        var order = inOrder(transactions, gateway);
        order.verify(transactions).getTransaction(any());
        order.verify(transactions).commit(any());
        order.verify(gateway).payWithBillingKey(eq(savedPayment.getId().toString()), eq("billing-key"), eq(12000L));
        order.verify(transactions).getTransaction(any());
        order.verify(transactions).commit(any());
        assertThat(result.pgTxId()).isEqualTo("pg-123");
    }

    @Test
    void 외부_호출_오류는_시작_기록을_롤백하거나_실패로_단정하지_않는다() {
        결제수단("billing-key");
        when(gateway.payWithBillingKey(anyString(), anyString(), anyLong()))
                .thenThrow(new BusinessException(PaymentErrorCode.GATEWAY_ERROR));
        assertThatThrownBy(() -> service.prePay(1L, 10L)).isInstanceOf(BusinessException.class);
        verify(transactions).commit(any());
        verify(transactions, never()).rollback(any());
        var saved = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getValue().getPgTxId()).isNull();
    }

    @Test
    void 승인한_사전결제의_요청번호로_전액취소를_호출한다() {
        결제수단("billing-key");
        service.prePay(1L, 10L);
        String requestPaymentId = savedPayment.getId().toString();
        assertThat(requestPaymentId).isEqualTo("123");
        when(gateway.cancelPayment(savedPayment.getId().toString(), "사전 결제 취소"))
                .thenReturn("{\"cancellation\":{\"status\":\"SUCCEEDED\",\"totalAmount\":12000}}");
        clearInvocations(transactions, payments, gateway);
        assertThat(service.cancelPrepayment(1L, 123L).verified()).isTrue();
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        var order = inOrder(gateway, transactions, payments);
        order.verify(payments).findById(123L);
        order.verify(gateway).cancelPayment(requestPaymentId, "사전 결제 취소");
        order.verify(transactions).getTransaction(any());
        order.verify(payments).findById(123L);
        order.verify(transactions).commit(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"cancellation\":{\"status\":\"REQUESTED\",\"totalAmount\":12000}}",
            "{\"cancellation\":{\"status\":\"FAILED\",\"totalAmount\":12000}}",
            "{\"cancellation\":{\"status\":\"SUCCEEDED\",\"totalAmount\":1000}}",
            "{}", "not-json"
    })
    void 전액취소_완료가_확인되지_않으면_성공을_반환하지_않는다(String response) {
        결제수단("billing-key");
        service.prePay(1L, 10L);
        when(gateway.cancelPayment(savedPayment.getId().toString(), "사전 결제 취소")).thenReturn(response);
        clearInvocations(transactions);
        assertThatThrownBy(() -> service.cancelPrepayment(1L, 123L))
                .isInstanceOf(BusinessException.class);
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(transactions);
    }

    @Test
    void 취소상태_커밋에_실패하면_검증성공을_반환하지_않는다() {
        결제수단("billing-key");
        service.prePay(1L, 10L);
        when(gateway.cancelPayment(savedPayment.getId().toString(), "사전 결제 취소"))
                .thenReturn("{\"cancellation\":{\"status\":\"SUCCEEDED\",\"totalAmount\":12000}}");
        doThrow(new org.springframework.transaction.TransactionSystemException("commit failed"))
                .when(transactions).commit(any());
        assertThatThrownBy(() -> service.cancelPrepayment(1L, 123L))
                .isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
    }

    @Test
    void 다른_사용자의_결제는_취소를_요청하지_않는다() {
        결제수단("billing-key");
        service.prePay(1L, 10L);
        assertThatThrownBy(() -> service.cancelPrepayment(2L, 123L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(gateway, never()).cancelPayment(anyString(), anyString());
    }

    private void 결제수단(String billingKey) {
        PaymentMethod method = mock(PaymentMethod.class);
        when(method.getBillingKey()).thenReturn(billingKey);
        when(gateway.payWithBillingKey(anyString(), eq("billing-key"), eq(12000L)))
                .thenReturn(new PortOnePaymentResponse(new BillingKeyPaymentSummary(
                        "pg-123", "2026-10-29T15:00:00+09:00")));
        when(methods.findDefaultByUserId(1L)).thenReturn(Optional.of(method));
    }
}
