package team.codingforest.moyeota.settlement.ledger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.api.dto.RideSettlementCommand;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedgers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SettlementLedgerServiceUnitTest {
    private static final Long PARTY_ID = 1001L;
    private static final Long DRIVER_ID = 9L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-21T01:00:00.123456Z");

    @Mock
    private SettlementLedgers ledgers;

    @InjectMocks
    private SettlementLedgerService ledgerService;

    @DisplayName("record - 성공 시 명령의 값으로 미정산 원장을 저장하고 금액은 요금과 프로모션의 합이다")
    @Test
    void record() {

        /*
         * settings
         */
        doReturn(false).when(ledgers).existsByMatchId(eq(PARTY_ID));

        /*
         * test
         */
        ledgerService.record(new RideSettlementCommand(PARTY_ID, DRIVER_ID, 15000, 1000, OCCURRED_AT));

        /*
         * verify
         */
        SettlementLedger saved = captureSaved();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getSettlementId()).isNull();
        assertThat(saved.getMatchId()).isEqualTo(PARTY_ID);
        assertThat(saved.getTaxiDriverId()).isEqualTo(DRIVER_ID);
        assertThat(saved.getTotalFare()).isEqualTo(15000);
        assertThat(saved.getPlatformPromotion()).isEqualTo(1000);
        assertThat(saved.getSettlementAmount()).isEqualTo(16000);
        assertThat(saved.getOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @DisplayName("record - 운행 시각은 마이크로초까지만 남긴다")
    @Test
    void record_truncatesOccurredAt() {

        /*
         * settings
         */
        doReturn(false).when(ledgers).existsByMatchId(eq(PARTY_ID));

        /*
         * test
         */
        ledgerService.record(new RideSettlementCommand(PARTY_ID, DRIVER_ID, 15000, 0, Instant.parse("2026-10-21T01:00:00.123456789Z")));

        /*
         * verify
         */
        assertThat(captureSaved().getOccurredAt()).isEqualTo(Instant.parse("2026-10-21T01:00:00.123456Z"));
    }

    @DisplayName("record - 금액 경계값: 요금 1원, 프로모션 0원, int 최댓값까지 정확히 더한다")
    @ParameterizedTest
    @CsvSource({
            "1, 0, 1",
            "1, 1, 2",
            "2147483647, 0, 2147483647",
            "2147483646, 1, 2147483647"
    })
    void record_amountBoundary(int totalFare, int platformPromotion, int expected) {

        /*
         * settings
         */
        doReturn(false).when(ledgers).existsByMatchId(eq(PARTY_ID));

        /*
         * test
         */
        ledgerService.record(new RideSettlementCommand(PARTY_ID, DRIVER_ID, totalFare, platformPromotion, OCCURRED_AT));

        /*
         * verify
         */
        SettlementLedger saved = captureSaved();
        assertThat(saved.getSettlementAmount()).isEqualTo(expected);
        assertThat(saved.getTotalFare()).isEqualTo(totalFare);
        assertThat(saved.getPlatformPromotion()).isEqualTo(platformPromotion);
    }

    @DisplayName("record - 합이 int 를 넘으면 예외가 나고 저장하지 않는다")
    @Test
    void record_amountOverflow() {

        /*
         * test
         */
        assertThatThrownBy(() -> ledgerService.record(new RideSettlementCommand(PARTY_ID, DRIVER_ID, Integer.MAX_VALUE, 1, OCCURRED_AT)))

        /*
         * verify
         */
                .isInstanceOf(ArithmeticException.class);
        verifyNoInteractions(ledgers);
    }

    @DisplayName("record - 이미 기록된 운행이면 저장하지 않는다")
    @Test
    void record_alreadyRecorded() {

        /*
         * settings
         */
        doReturn(true).when(ledgers).existsByMatchId(eq(PARTY_ID));

        /*
         * test
         */
        ledgerService.record(new RideSettlementCommand(PARTY_ID, DRIVER_ID, 15000, 0, OCCURRED_AT));

        /*
         * verify
         */
        verify(ledgers).existsByMatchId(eq(PARTY_ID));
        verify(ledgers, never()).save(any());
    }

    @DisplayName("record - 요금이 0 이하면 INVALID_TOTAL_FARE 로 실패하고 저장소를 건드리지 않는다")
    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void record_invalidTotalFare(int totalFare) {

        /*
         * test
         */
        assertThatThrownBy(() -> ledgerService.record(new RideSettlementCommand(PARTY_ID, DRIVER_ID, totalFare, 0, OCCURRED_AT)))

        /*
         * verify
         */
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.INVALID_TOTAL_FARE));
        verifyNoInteractions(ledgers);
    }

    @DisplayName("record - 프로모션이 음수면 INVALID_PLATFORM_PROMOTION 으로 실패하고 저장소를 건드리지 않는다")
    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void record_invalidPromotion(int platformPromotion) {

        /*
         * test
         */
        assertThatThrownBy(() -> ledgerService.record(new RideSettlementCommand(PARTY_ID, DRIVER_ID, 15000, platformPromotion, OCCURRED_AT)))

        /*
         * verify
         */
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.INVALID_PLATFORM_PROMOTION));
        verifyNoInteractions(ledgers);
    }

    @DisplayName("record - 요금과 프로모션이 둘 다 틀리면 요금 오류가 먼저 난다")
    @Test
    void record_invalidFareAndPromotion() {

        /*
         * test
         */
        assertThatThrownBy(() -> ledgerService.record(new RideSettlementCommand(PARTY_ID, DRIVER_ID, 0, -1, OCCURRED_AT)))

        /*
         * verify
         */
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.INVALID_TOTAL_FARE));
    }

    @DisplayName("record - 운행 ID 가 null 이면 INVALID_SETTLEMENT_COMMAND 로 실패한다")
    @Test
    void record_nullPartyId() {

        /*
         * test
         */
        assertThatThrownBy(() -> ledgerService.record(new RideSettlementCommand(null, DRIVER_ID, 15000, 0, OCCURRED_AT)))

        /*
         * verify
         */
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.INVALID_SETTLEMENT_COMMAND));
        verifyNoInteractions(ledgers);
    }

    @DisplayName("record - 기사 ID 가 null 이면 INVALID_SETTLEMENT_COMMAND 로 실패한다")
    @Test
    void record_nullDriverId() {

        /*
         * test
         */
        assertThatThrownBy(() -> ledgerService.record(new RideSettlementCommand(PARTY_ID, null, 15000, 0, OCCURRED_AT)))

        /*
         * verify
         */
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.INVALID_SETTLEMENT_COMMAND));
        verifyNoInteractions(ledgers);
    }

    @DisplayName("record - 운행 시각이 null 이면 INVALID_SETTLEMENT_COMMAND 로 실패한다")
    @Test
    void record_nullOccurredAt() {

        /*
         * test
         */
        assertThatThrownBy(() -> ledgerService.record(new RideSettlementCommand(PARTY_ID, DRIVER_ID, 15000, 0, null)))

        /*
         * verify
         */
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.INVALID_SETTLEMENT_COMMAND));
        verifyNoInteractions(ledgers);
    }

    @DisplayName("record - null 값과 금액 오류가 같이 있으면 null 오류가 먼저 난다")
    @Test
    void record_nullAndInvalidFare() {

        /*
         * test
         */
        assertThatThrownBy(() -> ledgerService.record(new RideSettlementCommand(null, DRIVER_ID, 0, -1, OCCURRED_AT)))

        /*
         * verify
         */
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.INVALID_SETTLEMENT_COMMAND));
    }

    @DisplayName("record - 명령이 null 이면 예외가 나고 저장소를 건드리지 않는다")
    @Test
    void record_nullCommand() {

        /*
         * test
         */
        assertThatThrownBy(() -> ledgerService.record(null))

        /*
         * verify
         */
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(ledgers);
    }

    @DisplayName("record - 중복 조회가 실패하면 예외가 그대로 나가고 저장하지 않는다")
    @Test
    void record_existsThrows() {

        /*
         * settings
         */
        doThrow(new IllegalStateException("DB 오류")).when(ledgers).existsByMatchId(eq(PARTY_ID));

        /*
         * test
         */
        assertThatThrownBy(() -> ledgerService.record(new RideSettlementCommand(PARTY_ID, DRIVER_ID, 15000, 0, OCCURRED_AT)))

        /*
         * verify
         */
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 오류");
        verify(ledgers, never()).save(any());
    }

    @DisplayName("record - 저장이 실패하면 예외가 그대로 나간다")
    @Test
    void record_saveThrows() {

        /*
         * settings
         */
        doReturn(false).when(ledgers).existsByMatchId(eq(PARTY_ID));
        doThrow(new IllegalStateException("유니크 위반")).when(ledgers).save(any(SettlementLedger.class));

        /*
         * test
         */
        assertThatThrownBy(() -> ledgerService.record(new RideSettlementCommand(PARTY_ID, DRIVER_ID, 15000, 0, OCCURRED_AT)))

        /*
         * verify
         */
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("유니크 위반");
    }

    private SettlementLedger captureSaved() {
        ArgumentCaptor<SettlementLedger> captor = ArgumentCaptor.forClass(SettlementLedger.class);
        verify(ledgers).save(captor.capture());
        return captor.getValue();
    }
}
