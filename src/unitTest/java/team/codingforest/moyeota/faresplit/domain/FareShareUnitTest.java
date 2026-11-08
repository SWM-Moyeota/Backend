package team.codingforest.moyeota.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  탑승 기록에 채울 내 몫 - 정산 애그리거트(FareSplit.amountOf)와 같은 값을 내는지 본다
 */
class FareShareUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long PAYER_ID = 1L;
    private static final Long SENDER_ID = 2L;

    @DisplayName("amountOf - 받는 사람은 택시비에서 보내는 사람 몫을 뺀 금액, 보내는 사람은 1인당 금액이다")
    @Test
    void amountOf() {

        /*
         * settings
         */
        FareShare fare = new FareShare(PARTY_ID, PAYER_ID, 18_700, 6_240, 2);

        /*
         * test
         */
        int payerAmount = fare.amountOf(PAYER_ID);
        int senderAmount = fare.amountOf(SENDER_ID);

        /*
         * verify
         */
        assertThat(payerAmount).isEqualTo(6_220);
        assertThat(senderAmount).isEqualTo(6_240);
    }

    @DisplayName("amountOf - 정산 애그리거트의 내 몫(FareSplit.amountOf)과 같고, 나눈 금액의 합은 택시비다")
    @ParameterizedTest(name = "{0}원 / {1}명")
    @CsvSource({"10000, 2", "10001, 2", "18700, 3", "1000, 3", "12345, 4", "500000, 3"})
    void amountOf_sameAsAggregate(int totalFare, int riders) {

        /*
         * settings
         */
        int perPerson = FareSplit.amountPerPerson(totalFare, riders);
        List<FareSplitTransfer> transfers = LongStream.rangeClosed(2, riders)
                .mapToObj(id -> FareSplitTransfer.load(id, null, null, null, null))
                .toList();
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, totalFare, perPerson, Bank.KB, null, FareSplitStatus.COMPLETED,
                Instant.parse("2026-11-03T12:00:00Z"), Instant.parse("2026-11-03T13:00:00Z"), transfers);
        FareShare fare = new FareShare(PARTY_ID, PAYER_ID, totalFare, perPerson, riders - 1);

        /*
         * test
         */
        int payerAmount = fare.amountOf(PAYER_ID);
        int senderAmount = fare.amountOf(SENDER_ID);

        /*
         * verify
         */
        assertThat(payerAmount).isEqualTo(split.amountOf(PAYER_ID));
        assertThat(senderAmount).isEqualTo(split.amountOf(SENDER_ID));
        assertThat(payerAmount + senderAmount * (riders - 1)).as("나눈 금액의 합은 택시비").isEqualTo(totalFare);
    }
}
