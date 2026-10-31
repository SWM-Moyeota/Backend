package team.codingforest.moyeota.settlement.ledger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedgers;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Settlements;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SettlementCloseServiceUnitTest {
    private static final Long DRIVER_ID = 9L;
    private static final Long SETTLEMENT_ID = 100L;
    private static final SettlementPeriod WEEK = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 21));

    @Mock
    private Settlements settlements;
    @Mock
    private SettlementLedgers ledgers;

    @InjectMocks
    private SettlementCloseService closeService;

    // ---------- findDriversToClose ----------

    @DisplayName("findDriversToClose - 원장 저장소가 찾은 기사 목록을 그대로 돌려준다")
    @Test
    void findDriversToClose() {

        /*
         * settings
         */
        doReturn(List.of(9L, 10L)).when(ledgers).findDriverIdsToSettle(eq(WEEK));

        /*
         * test
         */
        List<Long> driverIds = closeService.findDriversToClose(WEEK);

        /*
         * verify
         */
        assertThat(driverIds).containsExactly(9L, 10L);
        verifyNoInteractions(settlements);
    }

    @DisplayName("findDriversToClose - 대상 기사가 없으면 빈 목록을 돌려준다")
    @Test
    void findDriversToClose_empty() {

        /*
         * settings
         */
        doReturn(List.of()).when(ledgers).findDriverIdsToSettle(eq(WEEK));

        /*
         * test
         */
        List<Long> driverIds = closeService.findDriversToClose(WEEK);

        /*
         * verify
         */
        assertThat(driverIds).isEmpty();
    }

    // ---------- close ----------

    @DisplayName("close - 성공 시 정산서를 만들고 원장을 담은 뒤 원장 합계로 금액을 확정해 저장한다")
    @Test
    void close() {

        /*
         * settings
         */
        Settlement saved = savedSettlement();
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(saved).when(settlements).save(any(Settlement.class));
        doReturn(2).when(ledgers).assignToSettlement(eq(DRIVER_ID), eq(WEEK.end()), eq(SETTLEMENT_ID));
        doReturn(45000).when(ledgers).sumSettlementAmount(eq(SETTLEMENT_ID));

        /*
         * test
         */
        boolean created = closeService.close(DRIVER_ID, WEEK);

        /*
         * verify
         */
        assertThat(created).isTrue();

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        InOrder order = inOrder(settlements, ledgers);
        order.verify(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        order.verify(settlements).save(captor.capture());
        order.verify(ledgers).assignToSettlement(eq(DRIVER_ID), eq(WEEK.end()), eq(SETTLEMENT_ID));
        order.verify(ledgers).sumSettlementAmount(eq(SETTLEMENT_ID));
        order.verify(settlements).save(captor.capture());

        Settlement first = captor.getAllValues().get(0);
        assertThat(first.getId()).isNull();
        assertThat(first.getTaxiDriverId()).isEqualTo(DRIVER_ID);
        assertThat(first.getPeriodStart()).isEqualTo(WEEK.start());
        assertThat(first.getPeriodEnd()).isEqualTo(WEEK.end());
        assertThat(first.getAmount()).isZero();
        assertThat(first.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);

        Settlement second = captor.getAllValues().get(1);
        assertThat(second).isSameAs(saved);
        assertThat(second.getAmount()).isEqualTo(45000);
        assertThat(second.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
    }

    @DisplayName("close - 그 주 정산서가 이미 있으면 상태와 상관없이 아무것도 하지 않고 false 를 돌려준다")
    @ParameterizedTest
    @EnumSource(SettlementStatus.class)
    void close_alreadyClosed(SettlementStatus status) {

        /*
         * settings
         */
        Settlement existing = Settlement.load(SETTLEMENT_ID, DRIVER_ID, WEEK.start(), WEEK.end(), 45000, status, null, null);
        doReturn(Optional.of(existing)).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));

        /*
         * test
         */
        boolean created = closeService.close(DRIVER_ID, WEEK);

        /*
         * verify
         */
        assertThat(created).isFalse();
        verify(settlements, never()).save(any());
        verifyNoInteractions(ledgers);
    }

    @DisplayName("close - 담긴 원장이 0건이면 예외가 나고 금액을 확정하지 않는다")
    @Test
    void close_noLedgerAssigned() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(savedSettlement()).when(settlements).save(any(Settlement.class));
        doReturn(0).when(ledgers).assignToSettlement(eq(DRIVER_ID), eq(WEEK.end()), eq(SETTLEMENT_ID));

        /*
         * test
         */
        assertThatThrownBy(() -> closeService.close(DRIVER_ID, WEEK))

        /*
         * verify
         */
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("driverId=9")
                .hasMessageContaining("periodStart=2026-10-19");
        verify(ledgers, never()).sumSettlementAmount(anyLong());
        verify(settlements, times(1)).save(any());
    }

    @DisplayName("close - 담긴 원장이 1건이어도 정산서를 만든다")
    @Test
    void close_oneLedgerAssigned() {

        /*
         * settings
         */
        Settlement saved = savedSettlement();
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(saved).when(settlements).save(any(Settlement.class));
        doReturn(1).when(ledgers).assignToSettlement(eq(DRIVER_ID), eq(WEEK.end()), eq(SETTLEMENT_ID));
        doReturn(12000).when(ledgers).sumSettlementAmount(eq(SETTLEMENT_ID));

        /*
         * test
         */
        boolean created = closeService.close(DRIVER_ID, WEEK);

        /*
         * verify
         */
        assertThat(created).isTrue();
        assertThat(saved.getAmount()).isEqualTo(12000);
    }

    @DisplayName("close - 원장 합계가 0 이하면 금액을 확정하지 않고 예외가 난다")
    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void close_nonPositiveTotal(int total) {

        /*
         * settings
         */
        Settlement saved = savedSettlement();
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(saved).when(settlements).save(any(Settlement.class));
        doReturn(1).when(ledgers).assignToSettlement(eq(DRIVER_ID), eq(WEEK.end()), eq(SETTLEMENT_ID));
        doReturn(total).when(ledgers).sumSettlementAmount(eq(SETTLEMENT_ID));

        /*
         * test
         */
        assertThatThrownBy(() -> closeService.close(DRIVER_ID, WEEK))

        /*
         * verify
         */
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ledgerTotal=" + total);
        assertThat(saved.getAmount()).isZero();
        verify(settlements, times(1)).save(any());
    }

    @DisplayName("close - 원장 합계의 경계값 1원과 int 최댓값은 그대로 금액이 된다")
    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE})
    void close_totalBoundary(int total) {

        /*
         * settings
         */
        Settlement saved = savedSettlement();
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(saved).when(settlements).save(any(Settlement.class));
        doReturn(1).when(ledgers).assignToSettlement(eq(DRIVER_ID), eq(WEEK.end()), eq(SETTLEMENT_ID));
        doReturn(total).when(ledgers).sumSettlementAmount(eq(SETTLEMENT_ID));

        /*
         * test
         */
        boolean created = closeService.close(DRIVER_ID, WEEK);

        /*
         * verify
         */
        assertThat(created).isTrue();
        assertThat(saved.getAmount()).isEqualTo(total);
    }

    @DisplayName("close - 첫 저장이 실패하면 예외가 그대로 나가고 원장을 건드리지 않는다")
    @Test
    void close_firstSaveThrows() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doThrow(new IllegalStateException("유니크 위반")).when(settlements).save(any(Settlement.class));

        /*
         * test
         */
        assertThatThrownBy(() -> closeService.close(DRIVER_ID, WEEK))

        /*
         * verify
         */
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("유니크 위반");
        verifyNoInteractions(ledgers);
    }

    @DisplayName("close - 원장 담기가 실패하면 예외가 그대로 나가고 합계를 구하지 않는다")
    @Test
    void close_assignThrows() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(savedSettlement()).when(settlements).save(any(Settlement.class));
        doThrow(new IllegalStateException("DB 오류")).when(ledgers).assignToSettlement(eq(DRIVER_ID), eq(WEEK.end()), eq(SETTLEMENT_ID));

        /*
         * test
         */
        assertThatThrownBy(() -> closeService.close(DRIVER_ID, WEEK))

        /*
         * verify
         */
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 오류");
        verify(ledgers, never()).sumSettlementAmount(anyLong());
        verify(settlements, times(1)).save(any());
    }

    @DisplayName("close - 합계가 int 를 넘어 저장소가 실패하면 예외가 그대로 나가고 금액을 저장하지 않는다")
    @Test
    void close_sumOverflow() {

        /*
         * settings
         */
        Settlement saved = savedSettlement();
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(saved).when(settlements).save(any(Settlement.class));
        doReturn(1).when(ledgers).assignToSettlement(eq(DRIVER_ID), eq(WEEK.end()), eq(SETTLEMENT_ID));
        doThrow(new ArithmeticException("integer overflow")).when(ledgers).sumSettlementAmount(eq(SETTLEMENT_ID));

        /*
         * test
         */
        assertThatThrownBy(() -> closeService.close(DRIVER_ID, WEEK))

        /*
         * verify
         */
                .isInstanceOf(ArithmeticException.class);
        assertThat(saved.getAmount()).isZero();
        verify(settlements, times(1)).save(any());
    }

    @DisplayName("close - 금액 저장이 실패하면 예외가 그대로 나간다")
    @Test
    void close_secondSaveThrows() {

        /*
         * settings
         */
        Settlement saved = savedSettlement();
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(saved).doThrow(new IllegalStateException("DB 오류")).when(settlements).save(any(Settlement.class));
        doReturn(1).when(ledgers).assignToSettlement(eq(DRIVER_ID), eq(WEEK.end()), eq(SETTLEMENT_ID));
        doReturn(12000).when(ledgers).sumSettlementAmount(eq(SETTLEMENT_ID));

        /*
         * test
         */
        assertThatThrownBy(() -> closeService.close(DRIVER_ID, WEEK))

        /*
         * verify
         */
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 오류");
        verify(settlements, times(2)).save(any());
    }

    @DisplayName("close - 조회가 실패하면 예외가 그대로 나가고 아무것도 저장하지 않는다")
    @Test
    void close_findThrows() {

        /*
         * settings
         */
        doThrow(new IllegalStateException("DB 오류")).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));

        /*
         * test
         */
        assertThatThrownBy(() -> closeService.close(DRIVER_ID, WEEK))

        /*
         * verify
         */
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 오류");
        verify(settlements, never()).save(any());
        verify(ledgers, never()).assignToSettlement(anyLong(), any(), anyLong());
        verify(ledgers, never()).sumSettlementAmount(anyLong());
    }

    @DisplayName("close - 기간이 null 이면 예외가 나고 아무것도 저장하지 않는다")
    @Test
    void close_nullWeek() {

        /*
         * test
         */
        assertThatThrownBy(() -> closeService.close(DRIVER_ID, null))

        /*
         * verify
         */
                .isInstanceOf(NullPointerException.class);
        verify(settlements, never()).save(any());
        verifyNoInteractions(ledgers);
    }

    private static Settlement savedSettlement() {
        return Settlement.load(SETTLEMENT_ID, DRIVER_ID, WEEK.start(), WEEK.end(), 0, SettlementStatus.CONFIRMED, null, null);
    }
}
