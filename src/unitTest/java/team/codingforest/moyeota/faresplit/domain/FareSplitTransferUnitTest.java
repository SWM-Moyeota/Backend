package team.codingforest.moyeota.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  송금 상태는 저장하지 않고 sent_at·confirmed_at 으로 가른다
 */
class FareSplitTransferUnitTest {
    private static final Long MEMBER_ID = 2L;
    private static final Instant SENT_AT = Instant.parse("2026-11-03T13:00:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-11-03T14:00:00Z");

    @DisplayName("status - 보낸 시각도 확인 시각도 없으면 NOT_SENT")
    @Test
    void status_notSent() {

        /*
         * settings
         */
        FareSplitTransfer transfer = FareSplitTransfer.load(MEMBER_ID, null, null, null, null);

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
        FareSplitTransfer transfer = FareSplitTransfer.load(MEMBER_ID, SENT_AT, null, null, null);

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
        FareSplitTransfer transfer = FareSplitTransfer.load(MEMBER_ID, SENT_AT, CONFIRMED_AT, null, null);

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
        FareSplitTransfer transfer = FareSplitTransfer.load(MEMBER_ID, null, CONFIRMED_AT, null, null);

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
