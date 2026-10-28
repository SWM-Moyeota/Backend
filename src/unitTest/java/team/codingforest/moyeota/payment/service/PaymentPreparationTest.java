package team.codingforest.moyeota.payment.service;

import org.junit.jupiter.api.BeforeEach;
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
import team.codingforest.moyeota.payment.repository.PaymentMethodRepository;
import team.codingforest.moyeota.payment.repository.PaymentRepository;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 준비 단계의 분기만 검증한다. 실제 DB 커밋·결제 성공을 검증하는 테스트는 아니다. */
class PaymentPreparationTest {
    private final PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
    private final PaymentGroupRepository groups = mock(PaymentGroupRepository.class);
    private final PortOneClient gateway = mock(PortOneClient.class);
    private final UserAccess users = mock(UserAccess.class);
    private final PartyAccess parties = mock(PartyAccess.class);
    private final PaymentService service = new PaymentService(
            mock(PaymentRepository.class), gateway, users, parties, methods, groups);

    @BeforeEach
    void 준비() {
        when(users.findMemberSummaries(List.of(1L)))
                .thenReturn(Map.of(1L, new MemberSummary(UUID.randomUUID(), "사용자", null, null)));
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
        assertThatThrownBy(() -> service.prePay(1L, 10L)).isInstanceOf(UnsupportedOperationException.class);
        verify(groups).findByMatchId(10L);
        verify(groups, never()).save(any());
        verifyNoInteractions(gateway);
    }

    @Test
    void 그룹이_없으면_현재_인원과_미배정_기사로_생성한다() {
        결제수단("billing-key");
        when(groups.findByMatchId(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.prePay(1L, 10L)).isInstanceOf(UnsupportedOperationException.class);
        var captor = org.mockito.ArgumentCaptor.forClass(PaymentGroup.class);
        verify(groups).save(captor.capture());
        PaymentGroup group = captor.getValue();
        assertThat(group.getMatchId()).isEqualTo(10L);
        assertThat(group.getTaxiDriverId()).isNull();
        assertThat(group.getPassengerCount()).isEqualTo(3);
        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.PENDING);
        verifyNoInteractions(gateway);
    }

    private void 결제수단(String billingKey) {
        PaymentMethod method = mock(PaymentMethod.class);
        when(method.getBillingKey()).thenReturn(billingKey);
        when(methods.findDefaultByUserId(1L)).thenReturn(Optional.of(method));
    }
}
