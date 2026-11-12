package team.codingforest.moyeota.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  송금 상태는 저장하지 않고 sent_at·confirmed_at 으로 가른다. 보내는 사람의 새 줄은 부담 금액을 담고 첫 요청(요청 횟수 1)을 보낸 NOT_SENT 이고,
 *  받는 사람의 새 줄은 이미 낸 것이라 요청 없이(요청 횟수 0) 처음부터 CONFIRMED 다
 */
class FareSplitTransferUnitTest {
    private static final Long PAYER_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final int PAYMENT = 6_240;
    private static final int PAYER_PAYMENT = 6_220;
    private static final Instant CREATED_AT = Instant.parse("2026-11-03T12:40:00Z");
    private static final Instant SENT_AT = Instant.parse("2026-11-03T13:00:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-11-03T14:00:00Z");

    @DisplayName("create - 부담 금액을 담은 NOT_SENT 송금을 만들고 요청 횟수는 정산을 시작하며 보낸 첫 요청인 1이다")
    @Test
    void create() {

        /*
         * test
         */
        FareSplitTransfer transfer = FareSplitTransfer.create(MEMBER_ID, PAYMENT);

        /*
         * verify
         */
        assertThat(transfer.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(transfer.getPayment()).isEqualTo(PAYMENT);
        assertThat(transfer.getRequestCount()).isEqualTo(1);
        assertThat(transfer.getSentAt()).isNull();
        assertThat(transfer.getConfirmedAt()).isNull();
        assertThat(transfer.status()).isEqualTo(TransferStatus.NOT_SENT);
    }

    @DisplayName("createPaid - 받는 사람의 줄은 부담 금액을 담고 보낸 시각·확인 시각이 모두 정산 시작 시각인 CONFIRMED 다. 송금 요청을 보내지 않아 요청 횟수는 0이다")
    @Test
    void createPaid() {

        /*
         * test
         */
        FareSplitTransfer transfer = FareSplitTransfer.createPaid(PAYER_ID, PAYER_PAYMENT, CREATED_AT);

        /*
         * verify
         */
        assertThat(transfer.getMemberId()).isEqualTo(PAYER_ID);
        assertThat(transfer.getPayment()).isEqualTo(PAYER_PAYMENT);
        assertThat(transfer.getRequestCount()).isZero();
        assertThat(transfer.getSentAt()).isEqualTo(CREATED_AT);
        assertThat(transfer.getConfirmedAt()).isEqualTo(CREATED_AT);
        assertThat(transfer.status()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(transfer.isConfirmed()).isTrue();
        assertThat(transfer.isNotSent()).as("재요청 대상(NOT_SENT)이 아니다").isFalse();
        assertThat(transfer.isSent()).isFalse();
    }

    @DisplayName("load - 저장된 부담 금액·보낸 시각·확인 시각·요청 횟수를 그대로 담는다")
    @Test
    void load() {

        /*
         * test
         */
        FareSplitTransfer transfer = FareSplitTransfer.load(MEMBER_ID, PAYMENT, SENT_AT, CONFIRMED_AT, 4);

        /*
         * verify
         */
        assertThat(transfer.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(transfer.getPayment()).isEqualTo(PAYMENT);
        assertThat(transfer.getSentAt()).isEqualTo(SENT_AT);
        assertThat(transfer.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(transfer.getRequestCount()).isEqualTo(4);
    }

    @DisplayName("status - 보낸 시각도 확인 시각도 없으면 NOT_SENT")
    @Test
    void status_notSent() {

        /*
         * settings
         */
        FareSplitTransfer transfer = FareSplitTransfer.load(MEMBER_ID, PAYMENT, null, null, 1);

        /*
         * test
         */
        TransferStatus status = transfer.status();

        /*
         * verify
         */
        assertThat(status).isEqualTo(TransferStatus.NOT_SENT);
    }

    @DisplayName("status - 보낸 시각만 있으면 SENT")
    @Test
    void status_sent() {

        /*
         * settings
         */
        FareSplitTransfer transfer = FareSplitTransfer.load(MEMBER_ID, PAYMENT, SENT_AT, null, 1);

        /*
         * test
         */
        TransferStatus status = transfer.status();

        /*
         * verify
         */
        assertThat(status).isEqualTo(TransferStatus.SENT);
    }

    @DisplayName("status - 확인 시각이 있으면 CONFIRMED")
    @Test
    void status_confirmed() {

        /*
         * settings
         */
        FareSplitTransfer transfer = FareSplitTransfer.load(MEMBER_ID, PAYMENT, SENT_AT, CONFIRMED_AT, 1);

        /*
         * test
         */
        TransferStatus status = transfer.status();

        /*
         * verify
         */
        assertThat(status).isEqualTo(TransferStatus.CONFIRMED);
    }

    @DisplayName("status - 보낸 시각 없이 받는 사람이 바로 확인했어도(현금 등) CONFIRMED")
    @Test
    void status_confirmedWithoutSent() {

        /*
         * settings
         */
        FareSplitTransfer transfer = FareSplitTransfer.load(MEMBER_ID, PAYMENT, null, CONFIRMED_AT, 1);

        /*
         * test
         */
        TransferStatus status = transfer.status();

        /*
         * verify
         */
        assertThat(status).isEqualTo(TransferStatus.CONFIRMED);
    }
}
