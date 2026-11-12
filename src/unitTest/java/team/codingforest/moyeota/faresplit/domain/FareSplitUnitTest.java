package team.codingforest.moyeota.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitRole;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  정산 시작 규칙(방장·탑승자 자격, 탑승자마다 넣은 부담 금액의 대상·합)과 검사 순서, 탑승자 전원의 장부(받는 사람의 줄 포함)와 부담 금액,
 *  역할·계좌 노출 규칙, 송금 처리(보냄·받음·못 받음)와 완료, 수정 조건, 송금 재요청 차례와 상한, 만료, 받는 사람 탈퇴 시 계좌 삭제
 */
class FareSplitUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long PAYER_ID = 1L;
    private static final Long RIDER_ID = 2L;
    private static final Long LATE_RIDER_ID = 3L;
    private static final Long ABSENT_ID = 4L;
    private static final Long CASH_RIDER_ID = 6L;
    private static final Long IDLE_RIDER_ID = 7L;
    private static final Long OUTSIDER_ID = 99L;
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant NOW = MEETING_AT.plus(Duration.ofMinutes(40));
    private static final Instant SENT_AT = NOW.plus(Duration.ofMinutes(5));
    private static final Instant LATER = NOW.plus(Duration.ofHours(2));
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");
    private static final AccountNumber NEW_ACCOUNT = new AccountNumber("110123456789");
    // 택시비 18,700원을 셋이 탔을 때 방장이 보내는 사람마다 넣은 금액(앱이 1/N 을 10원 단위로 올려 미리 채우는 값)
    private static final int PAYMENT = 6_240;
    // 그때 받는 사람(방장)이 부담하는 나머지 금액
    private static final int PAYER_PAYMENT = 6_220;
    // 받는 사람은 나머지를, 보내는 사람 둘(rider, late rider)은 같은 금액을 부담하는 입력. 합은 18,700원
    private static final List<FarePayment> EVEN_PAYMENTS = List.of(pay(PAYER_ID, PAYER_PAYMENT), pay(RIDER_ID, PAYMENT), pay(LATE_RIDER_ID, PAYMENT));
    // NOW(한국 시각 11-03 21:40)에 시작한 정산의 첫 재요청 차례(11-04 01:40). 새벽이어도 보낸다
    private static final Instant REMIND_DUE = NOW.plus(FareSplit.REMIND_INTERVAL);

    // ───────────────────────── create ─────────────────────────

    @DisplayName("create - 방장을 포함한 탑승자마다 줄을 합류 순으로 만든다. 줄마다 부담 금액은 방장이 그 사람에게 넣은 금액(사람마다 달라도 된다)이고, 보내는 사람의 줄은 NOT_SENT·요청 횟수 1이다")
    @Test
    void create() {

        /*
         * settings
         */
        RideRoster party = threeRiders();

        /*
         * test
         */
        FareSplit split = FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT,
                List.of(pay(LATE_RIDER_ID, 5_000), pay(PAYER_ID, 6_700), pay(RIDER_ID, 7_000)), NOW);

        /*
         * verify
         */
        assertThat(split.getPartyId()).isEqualTo(PARTY_ID);
        assertThat(split.getPayerId()).isEqualTo(PAYER_ID);
        assertThat(split.getTotalFare()).isEqualTo(18_700);
        assertThat(split.getBank()).isEqualTo(Bank.KAKAO);
        assertThat(split.getAccountNumber()).isEqualTo(ACCOUNT);
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(split.isOpen()).isTrue();
        assertThat(split.getCreatedAt()).isEqualTo(NOW);
        assertThat(split.getClosedAt()).isNull();
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(PAYER_ID, RIDER_ID, LATE_RIDER_ID);
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::getPayment).as("각자에게 넣은 금액 그대로").containsExactly(6_700, 7_000, 5_000);
        assertThat(split.payments().stream().mapToInt(FarePayment::amount).sum()).as("부담 금액의 합은 택시비").isEqualTo(18_700);
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(RIDER_ID, LATE_RIDER_ID);
        assertThat(split.senderTransfers()).allSatisfy(t -> {
            assertThat(t.status()).isEqualTo(TransferStatus.NOT_SENT);
            assertThat(t.getRequestCount()).as("정산을 시작하며 보낸 첫 요청").isEqualTo(1);
            assertThat(t.getSentAt()).isNull();
            assertThat(t.getConfirmedAt()).isNull();
        });
    }

    @DisplayName("create - 받는 사람(방장)의 줄은 택시비를 이미 냈으니 시작 시각에 CONFIRMED 로 만들고 송금 요청을 보내지 않는다(요청 횟수 0)")
    @Test
    void create_payerTransfer() {

        /*
         * settings
         */
        RideRoster party = threeRiders();

        /*
         * test
         */
        FareSplit split = FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT,
                List.of(pay(PAYER_ID, 6_700), pay(RIDER_ID, 7_000), pay(LATE_RIDER_ID, 5_000)), NOW);

        /*
         * verify
         */
        FareSplitTransfer payerTransfer = split.findTransfer(PAYER_ID).orElseThrow();
        assertThat(payerTransfer.getPayment()).isEqualTo(6_700);
        assertThat(payerTransfer.status()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(payerTransfer.getSentAt()).isEqualTo(NOW);
        assertThat(payerTransfer.getConfirmedAt()).isEqualTo(NOW);
        assertThat(payerTransfer.getRequestCount()).isZero();
        assertThat(split.senderTransfers()).as("보내는 사람의 줄에는 끼지 않는다").doesNotContain(payerTransfer);
        assertThat(split.senderIds()).containsExactly(RIDER_ID, LATE_RIDER_ID);
        assertThat(split.getStatus()).as("보내는 사람이 남아 있어 열려 있다").isEqualTo(FareSplitStatus.OPEN);
    }

    @DisplayName("create - 도착을 누르지 않은 멤버는 탑승자가 아니라 장부에서 빠진다. 금액은 탑승자 것만 넣는다")
    @Test
    void create_excludesAbsentMember() {

        /*
         * settings
         */
        RideRoster party = withAbsent();

        /*
         * test
         */
        FareSplit split = FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, List.of(pay(PAYER_ID, 9_400), pay(RIDER_ID, 9_300)), NOW);

        /*
         * verify
         */
        assertThat(split.participantIds()).containsExactly(PAYER_ID, RIDER_ID);
        assertThat(split.senderIds()).containsExactly(RIDER_ID);
        assertThat(split.findTransfer(ABSENT_ID)).isEmpty();
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getPayment()).isEqualTo(9_300);
        assertThat(split.amountOf(PAYER_ID)).isEqualTo(9_400);
    }

    @DisplayName("create - 방장이 합류 순 중간이면 방장의 줄도 합류 순 그 자리에 담는다. 보내는 사람은 방장을 뺀 나머지다")
    @Test
    void create_hostInMiddle() {

        /*
         * settings
         */
        RideRoster party = finished(RIDER_ID, List.of(PAYER_ID, RIDER_ID, LATE_RIDER_ID), List.of(PAYER_ID, RIDER_ID, LATE_RIDER_ID));

        /*
         * test
         */
        FareSplit split = FareSplit.create(party, RIDER_ID, 10_000, Bank.KB, ACCOUNT,
                List.of(pay(PAYER_ID, 3_000), pay(RIDER_ID, 2_500), pay(LATE_RIDER_ID, 4_500)), NOW);

        /*
         * verify
         */
        assertThat(split.getPayerId()).isEqualTo(RIDER_ID);
        assertThat(split.senderIds()).containsExactly(PAYER_ID, LATE_RIDER_ID);
        assertThat(split.participantIds()).containsExactly(PAYER_ID, RIDER_ID, LATE_RIDER_ID);
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::getPayment).containsExactly(3_000, 2_500, 4_500);
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::status)
                .containsExactly(TransferStatus.NOT_SENT, TransferStatus.CONFIRMED, TransferStatus.NOT_SENT);
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::getPayment).containsExactly(3_000, 4_500);
    }

    @DisplayName("create - 방장이 아니면(함께 탄 멤버·도착하지 않은 멤버·방 밖) NOT_PARTY_HOST")
    @ParameterizedTest
    @ValueSource(longs = {2L, 4L, 99L})
    void create_notHost(Long memberId) {

        /*
         * settings
         */
        RideRoster party = withAbsent();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.create(party, memberId, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(RIDER_ID, 5_000)), NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_PARTY_HOST);
    }

    @DisplayName("create - 방장이 없는 방(hostId 가 null)이면 누구든 NOT_PARTY_HOST")
    @Test
    void create_noHost() {

        /*
         * settings
         */
        RideRoster party = finished(null, List.of(PAYER_ID, RIDER_ID), List.of(PAYER_ID, RIDER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(RIDER_ID, 5_000)), NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_PARTY_HOST);
    }

    @DisplayName("create - 동승이 끝나지 않은(FINISHED 가 아닌) 방이면 PARTY_NOT_FINISHED")
    @Test
    void create_notFinished() {

        /*
         * settings
         */
        RideRoster party = new RideRoster(PARTY_ID, PAYER_ID, false, List.of(PAYER_ID, RIDER_ID), List.of(PAYER_ID, RIDER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(RIDER_ID, 5_000)), NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.PARTY_NOT_FINISHED);
    }

    @DisplayName("create - 방장이 타지 않았으면(도착을 누르지 않음) NOT_A_RIDER")
    @Test
    void create_hostAbsent() {

        /*
         * settings
         */
        RideRoster party = finished(ABSENT_ID, List.of(PAYER_ID, ABSENT_ID, RIDER_ID), List.of(PAYER_ID, RIDER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.create(party, ABSENT_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(RIDER_ID, 5_000)), NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_A_RIDER);
    }

    @DisplayName("create - 아무도 출발을 누르지 않아 자동 정리된 방은 아무도 탑승자가 아니라 NOT_A_RIDER")
    @Test
    void create_noRiders() {

        /*
         * settings
         */
        RideRoster party = finished(PAYER_ID, List.of(PAYER_ID, RIDER_ID), List.of());

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(RIDER_ID, 5_000)), NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_A_RIDER);
    }

    @DisplayName("create - 함께 탄 사람이 방장 혼자면 NOT_ENOUGH_RIDERS")
    @Test
    void create_aloneRider() {

        /*
         * settings
         */
        RideRoster party = finished(PAYER_ID, List.of(PAYER_ID, ABSENT_ID), List.of(PAYER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(RIDER_ID, 5_000)), NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_ENOUGH_RIDERS);
    }

    @DisplayName("create - 탑승자 중 금액이 빠진 사람이 있으면(받는 사람인 방장이 빠짐·보내는 사람이 빠짐·아무도 없음) PAYMENT_TARGET_MISMATCH. 남은 금액의 합이 택시비와 같아도 마찬가지다")
    @Test
    void create_paymentMissing() {

        /*
         * settings
         */
        RideRoster party = threeRiders();

        /*
         * test
         */
        Throwable payerMissing = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT,
                List.of(pay(RIDER_ID, 10_000), pay(LATE_RIDER_ID, 8_700)), NOW));
        Throwable senderMissing = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT,
                List.of(pay(PAYER_ID, 10_000), pay(RIDER_ID, 8_700)), NOW));
        Throwable empty = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, List.of(), NOW));

        /*
         * verify
         */
        assertBusinessException(payerMissing, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(senderMissing, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(empty, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
    }

    @DisplayName("create - 탑승자가 아닌 사람(도착하지 않은 멤버·방 밖)의 금액이 더 들어 있으면 PAYMENT_TARGET_MISMATCH")
    @ParameterizedTest
    @ValueSource(longs = {4L, 99L})
    void create_paymentForNonRider(Long extraId) {

        /*
         * settings
         */
        RideRoster party = finished(PAYER_ID, List.of(PAYER_ID, RIDER_ID, ABSENT_ID, LATE_RIDER_ID), List.of(PAYER_ID, RIDER_ID, LATE_RIDER_ID));
        // 탑승자 셋의 금액은 모두 있고 합도 택시비와 같다
        List<FarePayment> payments = List.of(pay(PAYER_ID, PAYER_PAYMENT - 100), pay(RIDER_ID, PAYMENT), pay(LATE_RIDER_ID, PAYMENT), pay(extraId, 100));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, payments, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
    }

    @DisplayName("create - 금액 수가 탑승자 수와 같아도 사람이 다르면(탑승자 대신 방 밖 사람) PAYMENT_TARGET_MISMATCH")
    @Test
    void create_paymentForWrongPerson() {

        /*
         * settings
         */
        RideRoster party = threeRiders();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT,
                List.of(pay(PAYER_ID, PAYER_PAYMENT), pay(RIDER_ID, PAYMENT), pay(OUTSIDER_ID, PAYMENT)), NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
    }

    @DisplayName("create - 같은 사람의 금액이 두 번 들어 있으면(수는 맞지만 한 명이 빠짐·수가 하나 많음) PAYMENT_TARGET_MISMATCH. 금액의 합이 택시비와 같아도 마찬가지다")
    @Test
    void create_duplicatePayment() {

        /*
         * settings
         */
        RideRoster party = threeRiders();
        // rider 가 두 번 있고 late rider 가 빠졌다. 수는 탑승자 수와 같고 합도 택시비와 같다
        List<FarePayment> duplicatedInsteadOfRider = List.of(pay(PAYER_ID, PAYER_PAYMENT), pay(RIDER_ID, PAYMENT), pay(RIDER_ID, PAYMENT));
        // 탑승자 셋이 모두 있고 rider 가 한 번 더 있다. 합은 택시비와 같다
        List<FarePayment> duplicatedExtra = List.of(pay(PAYER_ID, PAYER_PAYMENT - 100), pay(RIDER_ID, PAYMENT), pay(LATE_RIDER_ID, PAYMENT), pay(RIDER_ID, 100));

        /*
         * test
         */
        Throwable sameCount = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, duplicatedInsteadOfRider, NOW));
        Throwable oneMore = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, duplicatedExtra, NOW));

        /*
         * verify
         */
        assertBusinessException(sameCount, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(oneMore, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
    }

    @DisplayName("create - 금액을 넣은 순서가 합류 순과 달라도 줄은 합류 순으로 만들고 금액은 그 사람을 따라간다")
    @Test
    void create_paymentOrderIgnored() {

        /*
         * settings
         */
        // 합류 순 idle rider → payer → rider. userId 순서와도, 금액을 넣은 순서와도 다르다
        RideRoster party = finished(PAYER_ID, List.of(IDLE_RIDER_ID, PAYER_ID, RIDER_ID), List.of(IDLE_RIDER_ID, PAYER_ID, RIDER_ID));

        /*
         * test
         */
        FareSplit split = FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT,
                List.of(pay(RIDER_ID, 7_000), pay(IDLE_RIDER_ID, 5_000), pay(PAYER_ID, 6_700)), NOW);
        FareSplit reversed = FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT,
                List.of(pay(PAYER_ID, 6_700), pay(IDLE_RIDER_ID, 5_000), pay(RIDER_ID, 7_000)), NOW);

        /*
         * verify
         */
        assertThat(split.participantIds()).containsExactly(IDLE_RIDER_ID, PAYER_ID, RIDER_ID);
        assertThat(split.payments()).containsExactly(pay(IDLE_RIDER_ID, 5_000), pay(PAYER_ID, 6_700), pay(RIDER_ID, 7_000));
        assertThat(split.senderPayments()).containsExactly(pay(IDLE_RIDER_ID, 5_000), pay(RIDER_ID, 7_000));
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::status)
                .containsExactly(TransferStatus.NOT_SENT, TransferStatus.CONFIRMED, TransferStatus.NOT_SENT);
        assertThat(reversed.payments()).as("넣은 순서가 달라도 결과는 같다").isEqualTo(split.payments());
    }

    @DisplayName("create - 부담 금액의 합이 택시비와 1원이라도 다르면(작음·큼) PAYMENTS_NOT_EQUAL_FARE")
    @ParameterizedTest(name = "택시비 18700원, 합 {0}원")
    @ValueSource(ints = {18_699, 18_701, 12_001, 30_000})
    void create_paymentsNotEqualFare(int paymentsTotal) {

        /*
         * settings
         */
        RideRoster party = threeRiders();
        List<FarePayment> payments = List.of(pay(PAYER_ID, paymentsTotal - 12_000), pay(RIDER_ID, 7_000), pay(LATE_RIDER_ID, 5_000));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, payments, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.PAYMENTS_NOT_EQUAL_FARE);
    }

    @DisplayName("create - 검사 순서는 방장 → 방이 끝남 → 방장이 탑승자 → 탑승자 2명 이상 → 금액을 넣은 사람이 탑승자 전원과 같음 → 금액 합이 택시비와 같음이다")
    @Test
    void create_validationOrder() {

        /*
         * settings
         */
        // 끝나지 않았고 방장이 타지 않았으며 탑승자도 없는 방
        RideRoster unfinished = new RideRoster(PARTY_ID, PAYER_ID, false, List.of(PAYER_ID, RIDER_ID), List.of());
        // 끝났지만 방장이 타지 않았고 탑승자가 한 명뿐인 방
        RideRoster hostAbsent = finished(PAYER_ID, List.of(PAYER_ID, RIDER_ID), List.of(RIDER_ID));
        // 끝났고 방장 혼자 탄 방
        RideRoster alone = finished(PAYER_ID, List.of(PAYER_ID, RIDER_ID), List.of(PAYER_ID));
        // 셋이 탄 방
        RideRoster three = threeRiders();
        // late rider 의 금액이 빠졌고 합도 택시비와 다른 입력
        List<FarePayment> missingAndNotEqual = List.of(pay(PAYER_ID, 20_000), pay(RIDER_ID, 20_000));
        // 탑승자 전원의 금액이 있지만 합이 택시비와 다른 입력
        List<FarePayment> notEqual = List.of(pay(PAYER_ID, 20_000), pay(RIDER_ID, 20_000), pay(LATE_RIDER_ID, 1));

        /*
         * test
         */
        Throwable notHost = catchThrowable(() -> FareSplit.create(unfinished, RIDER_ID, 18_700, Bank.KB, ACCOUNT, missingAndNotEqual, NOW));
        Throwable notFinished = catchThrowable(() -> FareSplit.create(unfinished, PAYER_ID, 18_700, Bank.KB, ACCOUNT, missingAndNotEqual, NOW));
        Throwable notRider = catchThrowable(() -> FareSplit.create(hostAbsent, PAYER_ID, 18_700, Bank.KB, ACCOUNT, missingAndNotEqual, NOW));
        Throwable notEnough = catchThrowable(() -> FareSplit.create(alone, PAYER_ID, 18_700, Bank.KB, ACCOUNT, missingAndNotEqual, NOW));
        Throwable targetMismatch = catchThrowable(() -> FareSplit.create(three, PAYER_ID, 18_700, Bank.KB, ACCOUNT, missingAndNotEqual, NOW));
        Throwable notEqualFare = catchThrowable(() -> FareSplit.create(three, PAYER_ID, 18_700, Bank.KB, ACCOUNT, notEqual, NOW));

        /*
         * verify
         */
        assertBusinessException(notHost, FareSplitErrorCode.NOT_PARTY_HOST);
        assertBusinessException(notFinished, FareSplitErrorCode.PARTY_NOT_FINISHED);
        assertBusinessException(notRider, FareSplitErrorCode.NOT_A_RIDER);
        assertBusinessException(notEnough, FareSplitErrorCode.NOT_ENOUGH_RIDERS);
        assertBusinessException(targetMismatch, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(notEqualFare, FareSplitErrorCode.PAYMENTS_NOT_EQUAL_FARE);
    }

    // ───────────────────────── 부담 금액 ─────────────────────────

    @DisplayName("payments / senderPayments - 참여자마다 부담 금액을 줄이 담긴 순서(합류 순)로 준다. payments 는 받는 사람을 포함한 전원, senderPayments 는 받는 사람을 뺀 보내는 사람만이다")
    @Test
    void payments() {

        /*
         * settings
         */
        // 합류 순 idle rider → payer → rider. userId 순서와 다르다
        RideRoster party = finished(PAYER_ID, List.of(IDLE_RIDER_ID, PAYER_ID, RIDER_ID), List.of(IDLE_RIDER_ID, PAYER_ID, RIDER_ID));
        FareSplit split = FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT,
                List.of(pay(RIDER_ID, 7_000), pay(PAYER_ID, 6_700), pay(IDLE_RIDER_ID, 5_000)), NOW);

        /*
         * test
         */
        List<FarePayment> payments = split.payments();
        List<FarePayment> senderPayments = split.senderPayments();

        /*
         * verify
         */
        assertThat(payments).containsExactly(pay(IDLE_RIDER_ID, 5_000), pay(PAYER_ID, 6_700), pay(RIDER_ID, 7_000));
        assertThat(senderPayments).containsExactly(pay(IDLE_RIDER_ID, 5_000), pay(RIDER_ID, 7_000));
    }

    @DisplayName("payments / senderPayments - 불러온 정산은 송금 상태와 상관없이 모든 줄의 금액을 담는다. senderPayments 에는 받는 사람의 줄만 빠진다")
    @Test
    void payments_loaded() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(LATE_RIDER_ID, 7_000, null, null, 2),
                FareSplitTransfer.load(RIDER_ID, 5_000, SENT_AT, null, 1),
                FareSplitTransfer.load(CASH_RIDER_ID, 3_000, null, SENT_AT, 1));

        /*
         * test
         */
        List<FarePayment> payments = split.payments();
        List<FarePayment> senderPayments = split.senderPayments();

        /*
         * verify
         */
        assertThat(payments).containsExactly(
                pay(PAYER_ID, PAYER_PAYMENT), pay(LATE_RIDER_ID, 7_000), pay(RIDER_ID, 5_000), pay(CASH_RIDER_ID, 3_000));
        assertThat(senderPayments).containsExactly(pay(LATE_RIDER_ID, 7_000), pay(RIDER_ID, 5_000), pay(CASH_RIDER_ID, 3_000));
    }

    @DisplayName("amountOf - 받는 사람이든 보내는 사람이든 내 부담 금액은 방장이 나에게 넣은 금액 그대로이고, 각자 부담 금액의 합은 택시비다")
    @ParameterizedTest(name = "{0}원 = 받는 사람 {1}원 + 보내는 사람 {2}원·{3}원")
    @CsvSource({
            "18700, 6220, 6240, 6240",
            "18700, 6700, 7000, 5000",
            "18700, 1, 10000, 8699",
            "10001, 5000, 1, 5000",
            "500000, 100000, 300000, 100000"})
    void amountOf_perParticipant(int totalFare, int payerPayment, int riderPayment, int lateRiderPayment) {

        /*
         * settings
         */
        List<FarePayment> payments = List.of(pay(PAYER_ID, payerPayment), pay(RIDER_ID, riderPayment), pay(LATE_RIDER_ID, lateRiderPayment));

        /*
         * test
         */
        FareSplit split = FareSplit.create(threeRiders(), PAYER_ID, totalFare, Bank.KB, ACCOUNT, payments, NOW);

        /*
         * verify
         */
        assertThat(split.amountOf(PAYER_ID)).isEqualTo(payerPayment);
        assertThat(split.amountOf(RIDER_ID)).isEqualTo(riderPayment);
        assertThat(split.amountOf(LATE_RIDER_ID)).isEqualTo(lateRiderPayment);
        assertThat(split.amountOf(PAYER_ID) + split.amountOf(RIDER_ID) + split.amountOf(LATE_RIDER_ID)).as("각자 부담 금액의 합은 택시비").isEqualTo(totalFare);
    }

    @DisplayName("amountOf - 불러온 정산은 받는 사람도 자기 줄에 담긴 금액을 그대로 읽는다")
    @Test
    void amountOf_loaded() {

        /*
         * settings
         */
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 20_000, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, NOW, null,
                List.of(FareSplitTransfer.load(PAYER_ID, 6_660, NOW, NOW, 0),
                        FareSplitTransfer.load(RIDER_ID, 7_000, null, null, 1),
                        FareSplitTransfer.load(LATE_RIDER_ID, 6_340, SENT_AT, null, 2)));

        /*
         * test
         */
        int payerAmount = split.amountOf(PAYER_ID);
        int senderAmount = split.amountOf(LATE_RIDER_ID);

        /*
         * verify
         */
        assertThat(payerAmount).isEqualTo(6_660);
        assertThat(senderAmount).isEqualTo(6_340);
    }

    // ───────────────────────── 역할·내 몫 ─────────────────────────

    @DisplayName("roleOf - payer 는 PAYER, 그 밖에 줄이 있는 사람은 SENDER")
    @Test
    void roleOf() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        FareSplitRole payer = split.roleOf(PAYER_ID);
        FareSplitRole sender = split.roleOf(RIDER_ID);

        /*
         * verify
         */
        assertThat(payer).isEqualTo(FareSplitRole.PAYER);
        assertThat(sender).isEqualTo(FareSplitRole.SENDER);
    }

    @DisplayName("roleOf - 정산에 없는 사람(도착하지 않은 멤버·방 밖)은 NOT_FARE_SPLIT_PARTICIPANT")
    @ParameterizedTest
    @ValueSource(longs = {4L, 99L})
    void roleOf_notParticipant(Long memberId) {

        /*
         * settings
         */
        FareSplit split = FareSplit.create(withAbsent(), PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, List.of(pay(PAYER_ID, 9_350), pay(RIDER_ID, 9_350)), NOW);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.roleOf(memberId));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    @DisplayName("amountOf - 받는 사람도 보내는 사람도 자기 줄의 부담 금액")
    @Test
    void amountOf() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        int payerAmount = split.amountOf(PAYER_ID);
        int senderAmount = split.amountOf(LATE_RIDER_ID);

        /*
         * verify
         */
        assertThat(payerAmount).isEqualTo(PAYER_PAYMENT);
        assertThat(senderAmount).isEqualTo(PAYMENT);
    }

    @DisplayName("amountOf - 정산에 없는 사람은 NOT_FARE_SPLIT_PARTICIPANT")
    @Test
    void amountOf_notParticipant() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.amountOf(OUTSIDER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    @DisplayName("transferStatusOf - 보내는 사람은 자기 줄의 송금 상태를, 받는 사람은 보낼 것이 없어 빈 값을 받는다")
    @Test
    void transferStatusOf() {

        /*
         * settings
         */
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 26_240, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, NOW, null,
                List.of(FareSplitTransfer.load(PAYER_ID, 6_660, NOW, NOW, 0),
                        FareSplitTransfer.load(RIDER_ID, 7_000, null, null, 1),
                        FareSplitTransfer.load(LATE_RIDER_ID, 6_340, SENT_AT, null, 1),
                        FareSplitTransfer.load(CASH_RIDER_ID, 6_240, null, LATER, 1)));

        /*
         * test
         */
        Optional<TransferStatus> payer = split.transferStatusOf(PAYER_ID);
        Optional<TransferStatus> notSent = split.transferStatusOf(RIDER_ID);
        Optional<TransferStatus> sent = split.transferStatusOf(LATE_RIDER_ID);
        Optional<TransferStatus> confirmed = split.transferStatusOf(CASH_RIDER_ID);

        /*
         * verify
         */
        assertThat(payer).as("받는 사람의 줄은 장부상 CONFIRMED 지만 송금 상태로는 주지 않는다").isEmpty();
        assertThat(notSent).contains(TransferStatus.NOT_SENT);
        assertThat(sent).contains(TransferStatus.SENT);
        assertThat(confirmed).contains(TransferStatus.CONFIRMED);
    }

    @DisplayName("transferStatusOf - 정산에 없는 사람은 NOT_FARE_SPLIT_PARTICIPANT")
    @Test
    void transferStatusOf_notParticipant() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.transferStatusOf(OUTSIDER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    @DisplayName("validateParticipant - 받는 사람과 보내는 사람은 통과하고 정산에 없는 사람은 NOT_FARE_SPLIT_PARTICIPANT")
    @Test
    void validateParticipant() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable payer = catchThrowable(() -> split.validateParticipant(PAYER_ID));
        Throwable sender = catchThrowable(() -> split.validateParticipant(RIDER_ID));
        Throwable outsider = catchThrowable(() -> split.validateParticipant(OUTSIDER_ID));

        /*
         * verify
         */
        assertThat(payer).isNull();
        assertThat(sender).isNull();
        assertBusinessException(outsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    @DisplayName("validatePayer - 받는 사람만 통과한다. 보내는 사람은 NOT_FARE_SPLIT_PAYER, 정산에 없는 사람은 NOT_FARE_SPLIT_PARTICIPANT")
    @Test
    void validatePayer() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable payer = catchThrowable(() -> split.validatePayer(PAYER_ID));
        Throwable sender = catchThrowable(() -> split.validatePayer(RIDER_ID));
        Throwable outsider = catchThrowable(() -> split.validatePayer(OUTSIDER_ID));

        /*
         * verify
         */
        assertThat(payer).isNull();
        assertBusinessException(sender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(outsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    @DisplayName("isPayer - 받는 사람만 true")
    @Test
    void isPayer() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        boolean payer = split.isPayer(PAYER_ID);
        boolean sender = split.isPayer(RIDER_ID);
        boolean outsider = split.isPayer(OUTSIDER_ID);

        /*
         * verify
         */
        assertThat(payer).isTrue();
        assertThat(sender).isFalse();
        assertThat(outsider).isFalse();
    }

    @DisplayName("isOpen - OPEN 만 true, 닫힌 정산(COMPLETED·EXPIRED)은 false")
    @ParameterizedTest
    @CsvSource({"OPEN, true", "COMPLETED, false", "EXPIRED, false"})
    void isOpen(FareSplitStatus status, boolean expected) {

        /*
         * settings
         */
        FareSplit split = split(status, notSent(RIDER_ID));

        /*
         * test
         */
        boolean open = split.isOpen();

        /*
         * verify
         */
        assertThat(open).isEqualTo(expected);
    }

    // ───────────────────────── 계좌 노출 ─────────────────────────

    @DisplayName("receivingAccount - OPEN 이면 받을 계좌를 준다")
    @Test
    void receivingAccount_open() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        var account = split.receivingAccount();

        /*
         * verify
         */
        assertThat(account).contains(ACCOUNT);
    }

    @DisplayName("receivingAccount - 닫힌 정산(COMPLETED·EXPIRED)은 계좌가 남아 있어도 보여 주지 않는다")
    @ParameterizedTest
    @EnumSource(value = FareSplitStatus.class, names = {"COMPLETED", "EXPIRED"})
    void receivingAccount_closed(FareSplitStatus status) {

        /*
         * settings
         */
        FareSplit split = loadSplit(status, ACCOUNT, NOW.plusSeconds(60), notSent(RIDER_ID));

        /*
         * test
         */
        var account = split.receivingAccount();

        /*
         * verify
         */
        assertThat(account).isEmpty();
    }

    @DisplayName("receivingAccount - OPEN 이어도 계좌가 지워졌으면(null) 없다")
    @Test
    void receivingAccount_cleared() {

        /*
         * settings
         */
        FareSplit split = loadSplit(FareSplitStatus.OPEN, null, null, notSent(RIDER_ID));

        /*
         * test
         */
        var account = split.receivingAccount();

        /*
         * verify
         */
        assertThat(account).isEmpty();
    }

    // ───────────────────────── markSent ─────────────────────────

    @DisplayName("markSent - 아직 안 보낸 보내는 사람이 누르면 SENT 가 되고 보낸 시각을 남긴다. 요청 횟수는 그대로다")
    @Test
    void markSent() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        boolean changed = split.markSent(RIDER_ID, LATER);

        /*
         * verify
         */
        assertThat(changed).isTrue();
        FareSplitTransfer transfer = split.findTransfer(RIDER_ID).orElseThrow();
        assertThat(transfer.status()).isEqualTo(TransferStatus.SENT);
        assertThat(transfer.getSentAt()).isEqualTo(LATER);
        assertThat(transfer.getRequestCount()).isEqualTo(1);
        assertThat(split.findTransfer(LATE_RIDER_ID).orElseThrow().status()).as("다른 사람 송금은 그대로").isEqualTo(TransferStatus.NOT_SENT);
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
    }

    @DisplayName("markSent - 열린 정산에서 이미 SENT·CONFIRMED 면 시각을 바꾸지 않고 false(다시 눌러도 된다)")
    @Test
    void markSent_alreadySent() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                sent(RIDER_ID),
                FareSplitTransfer.load(LATE_RIDER_ID, PAYMENT, null, SENT_AT, 1),
                notSent(IDLE_RIDER_ID));

        /*
         * test
         */
        boolean sentAgain = split.markSent(RIDER_ID, LATER);
        boolean confirmedAgain = split.markSent(LATE_RIDER_ID, LATER);

        /*
         * verify
         */
        assertThat(sentAgain).isFalse();
        assertThat(confirmedAgain).isFalse();
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getSentAt()).isEqualTo(SENT_AT);
        assertThat(split.findTransfer(LATE_RIDER_ID).orElseThrow().getSentAt()).isNull();
        assertThat(split.findTransfer(LATE_RIDER_ID).orElseThrow().status()).isEqualTo(TransferStatus.CONFIRMED);
    }

    @DisplayName("markSent - 받는 사람이 누르면 NOT_FARE_SPLIT_SENDER")
    @Test
    void markSent_payer() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.markSent(PAYER_ID, LATER));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_FARE_SPLIT_SENDER);
        assertThat(split.findTransfer(PAYER_ID).orElseThrow().getSentAt()).as("받는 사람의 줄은 그대로").isEqualTo(NOW);
    }

    @DisplayName("markSent - 정산에 없는 사람이면 NOT_FARE_SPLIT_PARTICIPANT")
    @Test
    void markSent_notParticipant() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.markSent(OUTSIDER_ID, LATER));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    @DisplayName("markSent - 닫힌 정산(COMPLETED·EXPIRED)이면 아직 안 보냈든 이미 SENT·CONFIRMED 였든 FARE_SPLIT_CLOSED 이고 바꾸지 않는다")
    @ParameterizedTest
    @EnumSource(value = FareSplitStatus.class, names = {"COMPLETED", "EXPIRED"})
    void markSent_closed(FareSplitStatus status) {

        /*
         * settings
         */
        FareSplit split = split(status,
                notSent(RIDER_ID),
                sent(LATE_RIDER_ID),
                FareSplitTransfer.load(CASH_RIDER_ID, PAYMENT, null, SENT_AT, 1));

        /*
         * test
         */
        Throwable notSent = catchThrowable(() -> split.markSent(RIDER_ID, LATER));
        Throwable alreadySent = catchThrowable(() -> split.markSent(LATE_RIDER_ID, LATER));
        Throwable alreadyConfirmed = catchThrowable(() -> split.markSent(CASH_RIDER_ID, LATER));

        /*
         * verify
         */
        assertBusinessException(notSent, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertBusinessException(alreadySent, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertBusinessException(alreadyConfirmed, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().status()).isEqualTo(TransferStatus.NOT_SENT);
        assertThat(split.findTransfer(LATE_RIDER_ID).orElseThrow().getSentAt()).isEqualTo(SENT_AT);
        assertThat(split.findTransfer(CASH_RIDER_ID).orElseThrow().getSentAt()).isNull();
    }

    @DisplayName("markSent - 역할을 정산이 열려 있는지보다 먼저 본다. 닫힌 정산이어도 받는 사람은 NOT_FARE_SPLIT_SENDER, 정산에 없는 사람은 NOT_FARE_SPLIT_PARTICIPANT")
    @Test
    void markSent_roleBeforeClosed() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.EXPIRED, notSent(RIDER_ID));

        /*
         * test
         */
        Throwable byPayer = catchThrowable(() -> split.markSent(PAYER_ID, LATER));
        Throwable byOutsider = catchThrowable(() -> split.markSent(OUTSIDER_ID, LATER));

        /*
         * verify
         */
        assertBusinessException(byPayer, FareSplitErrorCode.NOT_FARE_SPLIT_SENDER);
        assertBusinessException(byOutsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    // ───────────────────────── confirmReceived ─────────────────────────

    @DisplayName("confirmReceived - SENT 송금을 확인하면 CONFIRMED 가 되고, 남은 송금이 있으면 정산은 OPEN 이고 계좌도 남는다")
    @Test
    void confirmReceived() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, sent(RIDER_ID), notSent(LATE_RIDER_ID));

        /*
         * test
         */
        boolean changed = split.confirmReceived(PAYER_ID, RIDER_ID, LATER);

        /*
         * verify
         */
        assertThat(changed).isTrue();
        FareSplitTransfer transfer = split.findTransfer(RIDER_ID).orElseThrow();
        assertThat(transfer.status()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(transfer.getSentAt()).isEqualTo(SENT_AT);
        assertThat(transfer.getConfirmedAt()).isEqualTo(LATER);
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(split.getClosedAt()).isNull();
        assertThat(split.getAccountNumber()).isEqualTo(ACCOUNT);
    }

    @DisplayName("confirmReceived - 보냈다고 누르기 전(NOT_SENT)이어도 바로 확인할 수 있다(현금 등). 보낸 시각은 비어 있다")
    @Test
    void confirmReceived_fromNotSent() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        boolean changed = split.confirmReceived(PAYER_ID, LATE_RIDER_ID, LATER);

        /*
         * verify
         */
        assertThat(changed).isTrue();
        FareSplitTransfer transfer = split.findTransfer(LATE_RIDER_ID).orElseThrow();
        assertThat(transfer.status()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(transfer.getSentAt()).isNull();
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
    }

    @DisplayName("confirmReceived - 보내는 사람의 마지막 송금을 확인하면 COMPLETED 로 닫고 닫힌 시각을 남기며 계좌번호를 지운다")
    @Test
    void confirmReceived_completes() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, PAYMENT, SENT_AT, SENT_AT.plusSeconds(60), 1),
                FareSplitTransfer.load(LATE_RIDER_ID, PAYMENT, SENT_AT, null, 3));

        /*
         * test
         */
        boolean changed = split.confirmReceived(PAYER_ID, LATE_RIDER_ID, LATER);

        /*
         * verify
         */
        assertThat(changed).isTrue();
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.COMPLETED);
        assertThat(split.isOpen()).isFalse();
        assertThat(split.getClosedAt()).isEqualTo(LATER);
        assertThat(split.getAccountNumber()).isNull();
        assertThat(split.receivingAccount()).isEmpty();
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::status).containsOnly(TransferStatus.CONFIRMED);
    }

    @DisplayName("confirmReceived - 받는 사람의 줄은 처음부터 CONFIRMED 라, 막 시작한 정산도 보내는 사람을 모두 확인하면 COMPLETED 로 닫힌다. 한 명이라도 남으면 OPEN 이다")
    @Test
    void confirmReceived_completesWithPayerTransfer() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        split.confirmReceived(PAYER_ID, RIDER_ID, SENT_AT);
        FareSplitStatus afterFirst = split.getStatus();
        split.confirmReceived(PAYER_ID, LATE_RIDER_ID, LATER);

        /*
         * verify
         */
        assertThat(afterFirst).isEqualTo(FareSplitStatus.OPEN);
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.COMPLETED);
        assertThat(split.getClosedAt()).isEqualTo(LATER);
        assertThat(split.findTransfer(PAYER_ID).orElseThrow().getConfirmedAt()).as("받는 사람 줄의 확인 시각은 시작 시각 그대로").isEqualTo(NOW);
    }

    @DisplayName("confirmReceived - 열린 정산에서 이미 CONFIRMED 면 확인 시각을 바꾸지 않고 false")
    @Test
    void confirmReceived_alreadyConfirmed() {

        /*
         * settings
         */
        Instant confirmedAt = SENT_AT.plusSeconds(60);
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, PAYMENT, SENT_AT, confirmedAt, 1),
                notSent(LATE_RIDER_ID));

        /*
         * test
         */
        boolean changed = split.confirmReceived(PAYER_ID, RIDER_ID, LATER);

        /*
         * verify
         */
        assertThat(changed).isFalse();
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(split.getClosedAt()).isNull();
    }

    @DisplayName("confirmReceived - 보내는 사람이 확인하면 NOT_FARE_SPLIT_PAYER, 정산에 없는 사람이면 NOT_FARE_SPLIT_PARTICIPANT")
    @Test
    void confirmReceived_notPayer() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable bySender = catchThrowable(() -> split.confirmReceived(RIDER_ID, LATE_RIDER_ID, LATER));
        Throwable byOutsider = catchThrowable(() -> split.confirmReceived(OUTSIDER_ID, LATE_RIDER_ID, LATER));

        /*
         * verify
         */
        assertBusinessException(bySender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(byOutsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::status).containsOnly(TransferStatus.NOT_SENT);
    }

    @DisplayName("confirmReceived - 이 정산의 보내는 사람이 아닌 사람(자기 줄이 있는 받는 사람 자신·방 밖)의 송금이면 FARE_SPLIT_TRANSFER_NOT_FOUND 이고 정산은 그대로다")
    @ParameterizedTest
    @ValueSource(longs = {1L, 99L})
    void confirmReceived_noTransfer(Long senderId) {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.confirmReceived(PAYER_ID, senderId, LATER));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
        assertThat(split.findTransfer(PAYER_ID).orElseThrow().getConfirmedAt()).isEqualTo(NOW);
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
    }

    @DisplayName("confirmReceived - 확인 전에 닫힌(EXPIRED) 정산이면 FARE_SPLIT_CLOSED")
    @Test
    void confirmReceived_closed() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.EXPIRED, sent(RIDER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.confirmReceived(PAYER_ID, RIDER_ID, LATER));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().status()).isEqualTo(TransferStatus.SENT);
    }

    @DisplayName("confirmReceived - 이미 COMPLETED 인 정산에서 확인된 송금을 다시 눌러도 FARE_SPLIT_CLOSED 이고 그대로다")
    @Test
    void confirmReceived_closedAlreadyConfirmed() {

        /*
         * settings
         */
        Instant closedAt = SENT_AT.plusSeconds(60);
        FareSplit split = loadSplit(FareSplitStatus.COMPLETED, null, closedAt,
                FareSplitTransfer.load(RIDER_ID, PAYMENT, SENT_AT, closedAt, 1),
                FareSplitTransfer.load(LATE_RIDER_ID, PAYMENT, null, closedAt, 1));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.confirmReceived(PAYER_ID, RIDER_ID, LATER));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getConfirmedAt()).isEqualTo(closedAt);
        assertThat(split.getClosedAt()).isEqualTo(closedAt);
    }

    @DisplayName("confirmReceived - 검사 순서는 받는 사람 → 정산이 열려 있음 → 대상 송금이다")
    @Test
    void confirmReceived_validationOrder() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.EXPIRED, sent(RIDER_ID));

        /*
         * test
         */
        Throwable bySender = catchThrowable(() -> split.confirmReceived(RIDER_ID, OUTSIDER_ID, LATER));
        Throwable byOutsider = catchThrowable(() -> split.confirmReceived(OUTSIDER_ID, OUTSIDER_ID, LATER));
        Throwable noTransfer = catchThrowable(() -> split.confirmReceived(PAYER_ID, OUTSIDER_ID, LATER));

        /*
         * verify
         */
        assertBusinessException(bySender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(byOutsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        assertBusinessException(noTransfer, FareSplitErrorCode.FARE_SPLIT_CLOSED);
    }

    // ───────────────────────── markNotReceived ─────────────────────────

    @DisplayName("markNotReceived - SENT 송금을 NOT_SENT 로 되돌리며 보낸 시각만 지운다. 요청 횟수와 보낼 금액은 그대로다")
    @Test
    void markNotReceived() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, PAYMENT, SENT_AT, null, 2),
                sent(LATE_RIDER_ID));

        /*
         * test
         */
        split.markNotReceived(PAYER_ID, RIDER_ID);

        /*
         * verify
         */
        FareSplitTransfer transfer = split.findTransfer(RIDER_ID).orElseThrow();
        assertThat(transfer.status()).isEqualTo(TransferStatus.NOT_SENT);
        assertThat(transfer.getSentAt()).isNull();
        assertThat(transfer.getConfirmedAt()).isNull();
        assertThat(transfer.getRequestCount()).isEqualTo(2);
        assertThat(transfer.getPayment()).isEqualTo(PAYMENT);
        assertThat(split.findTransfer(LATE_RIDER_ID).orElseThrow().status()).isEqualTo(TransferStatus.SENT);
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
    }

    @DisplayName("markNotReceived - SENT 가 아닌 송금(아직 안 보냄·이미 확인)이면 TRANSFER_NOT_SENT")
    @Test
    void markNotReceived_notSent() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                notSent(RIDER_ID),
                FareSplitTransfer.load(LATE_RIDER_ID, PAYMENT, SENT_AT, LATER, 1));

        /*
         * test
         */
        Throwable notSent = catchThrowable(() -> split.markNotReceived(PAYER_ID, RIDER_ID));
        Throwable confirmed = catchThrowable(() -> split.markNotReceived(PAYER_ID, LATE_RIDER_ID));

        /*
         * verify
         */
        assertBusinessException(notSent, FareSplitErrorCode.TRANSFER_NOT_SENT);
        assertBusinessException(confirmed, FareSplitErrorCode.TRANSFER_NOT_SENT);
        assertThat(split.findTransfer(LATE_RIDER_ID).orElseThrow().status()).isEqualTo(TransferStatus.CONFIRMED);
    }

    @DisplayName("markNotReceived - 보내는 사람이 누르면 NOT_FARE_SPLIT_PAYER, 정산에 없는 사람이면 NOT_FARE_SPLIT_PARTICIPANT")
    @Test
    void markNotReceived_notPayer() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, sent(RIDER_ID));

        /*
         * test
         */
        Throwable bySender = catchThrowable(() -> split.markNotReceived(RIDER_ID, RIDER_ID));
        Throwable byOutsider = catchThrowable(() -> split.markNotReceived(OUTSIDER_ID, RIDER_ID));

        /*
         * verify
         */
        assertBusinessException(bySender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(byOutsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().status()).isEqualTo(TransferStatus.SENT);
    }

    @DisplayName("markNotReceived - 이 정산의 보내는 사람이 아닌 사람(자기 줄이 있는 받는 사람 자신·방 밖)의 송금이면 FARE_SPLIT_TRANSFER_NOT_FOUND 이고 받는 사람의 줄은 그대로다")
    @ParameterizedTest
    @ValueSource(longs = {1L, 99L})
    void markNotReceived_noTransfer(Long senderId) {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, sent(RIDER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.markNotReceived(PAYER_ID, senderId));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
        assertThat(split.findTransfer(PAYER_ID).orElseThrow().status()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(split.findTransfer(PAYER_ID).orElseThrow().getSentAt()).isEqualTo(NOW);
    }

    @DisplayName("markNotReceived - 닫힌(EXPIRED) 정산이면 SENT 송금이어도, 없는 송금이어도 FARE_SPLIT_CLOSED. 보내는 사람이 누르면 그보다 먼저 NOT_FARE_SPLIT_PAYER")
    @Test
    void markNotReceived_closed() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.EXPIRED, sent(RIDER_ID));

        /*
         * test
         */
        Throwable sentTransfer = catchThrowable(() -> split.markNotReceived(PAYER_ID, RIDER_ID));
        Throwable noTransfer = catchThrowable(() -> split.markNotReceived(PAYER_ID, OUTSIDER_ID));
        Throwable bySender = catchThrowable(() -> split.markNotReceived(RIDER_ID, RIDER_ID));

        /*
         * verify
         */
        assertBusinessException(sentTransfer, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertBusinessException(noTransfer, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertBusinessException(bySender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getSentAt()).isEqualTo(SENT_AT);
    }

    // ───────────────────────── edit ─────────────────────────

    @DisplayName("edit - 아무도 보내기 전이면 택시비·은행·계좌를 바꾸고 줄마다 부담 금액을 그 사람에게 새로 넣은 금액으로 바꾼다. 받는 사람의 줄은 CONFIRMED 여도 금액이 바뀐다")
    @Test
    void edit() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, List.of(pay(LATE_RIDER_ID, 5_500), pay(PAYER_ID, 6_500), pay(RIDER_ID, 8_000)));

        /*
         * verify
         */
        assertThat(split.getTotalFare()).isEqualTo(20_000);
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(PAYER_ID, RIDER_ID, LATE_RIDER_ID);
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::getPayment).containsExactly(6_500, 8_000, 5_500);
        assertThat(split.payments()).containsExactly(pay(PAYER_ID, 6_500), pay(RIDER_ID, 8_000), pay(LATE_RIDER_ID, 5_500));
        assertThat(split.senderPayments()).containsExactly(pay(RIDER_ID, 8_000), pay(LATE_RIDER_ID, 5_500));
        assertThat(split.amountOf(PAYER_ID)).isEqualTo(6_500);
        assertThat(split.amountOf(RIDER_ID)).isEqualTo(8_000);
        assertThat(split.amountOf(LATE_RIDER_ID)).isEqualTo(5_500);
        assertThat(split.getBank()).isEqualTo(Bank.SHINHAN);
        assertThat(split.getAccountNumber()).isEqualTo(NEW_ACCOUNT);
        assertThat(split.receivingAccount()).contains(NEW_ACCOUNT);
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(split.getCreatedAt()).isEqualTo(NOW);
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::getRequestCount).as("요청 횟수는 그대로").containsOnly(1);
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::status).containsOnly(TransferStatus.NOT_SENT);
        FareSplitTransfer payerTransfer = split.findTransfer(PAYER_ID).orElseThrow();
        assertThat(payerTransfer.status()).as("받는 사람의 줄은 금액만 바뀐다").isEqualTo(TransferStatus.CONFIRMED);
        assertThat(payerTransfer.getSentAt()).isEqualTo(NOW);
        assertThat(payerTransfer.getConfirmedAt()).isEqualTo(NOW);
        assertThat(payerTransfer.getRequestCount()).isZero();
    }

    @DisplayName("edit - 보내는 사람이 한 명이면 받는 사람과 그 사람 금액만 넣는다(도착하지 않은 멤버는 참여자가 아니다)")
    @Test
    void edit_oneSender() {

        /*
         * settings
         */
        FareSplit split = FareSplit.create(withAbsent(), PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, List.of(pay(PAYER_ID, 9_350), pay(RIDER_ID, 9_350)), NOW);

        /*
         * test
         */
        split.edit(PAYER_ID, 10_001, Bank.KAKAO, ACCOUNT, List.of(pay(PAYER_ID, 4_991), pay(RIDER_ID, 5_010)));
        Throwable withAbsentMember = catchThrowable(() -> split.edit(PAYER_ID, 10_001, Bank.KAKAO, ACCOUNT,
                List.of(pay(PAYER_ID, 2_001), pay(RIDER_ID, 4_000), pay(ABSENT_ID, 4_000))));

        /*
         * verify
         */
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getPayment()).isEqualTo(5_010);
        assertThat(split.amountOf(PAYER_ID)).isEqualTo(4_991);
        assertBusinessException(withAbsentMember, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
    }

    @DisplayName("edit - 재요청을 받은(요청 횟수가 늘어난) 송금도 아직 안 보냈으면 고칠 수 있고 요청 횟수는 그대로다")
    @Test
    void edit_afterReminders() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, notSent(RIDER_ID, 3), notSent(LATE_RIDER_ID, 2));

        /*
         * test
         */
        split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, List.of(pay(PAYER_ID, 6_660), pay(RIDER_ID, 7_000), pay(LATE_RIDER_ID, 6_340)));

        /*
         * verify
         */
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::getPayment).containsExactly(7_000, 6_340);
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::getRequestCount).containsExactly(3, 2);
        assertThat(split.amountOf(PAYER_ID)).isEqualTo(6_660);
    }

    @DisplayName("edit - 참여자 중 금액이 빠진 사람이 있으면(받는 사람이 빠짐·보내는 사람이 빠짐·아무도 없음) PAYMENT_TARGET_MISMATCH 이고 값을 바꾸지 않는다")
    @Test
    void edit_paymentMissing() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable payerMissing = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, List.of(pay(RIDER_ID, 12_000), pay(LATE_RIDER_ID, 8_000))));
        Throwable senderMissing = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, List.of(pay(PAYER_ID, 13_000), pay(RIDER_ID, 7_000))));
        Throwable empty = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, List.of()));

        /*
         * verify
         */
        assertBusinessException(payerMissing, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(senderMissing, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(empty, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertUnchanged(split);
    }

    @DisplayName("edit - 참여자가 아닌 사람(방 밖)의 금액이 더 들어 있으면 PAYMENT_TARGET_MISMATCH 이고 값을 바꾸지 않는다")
    @Test
    void edit_paymentForNonParticipant() {

        /*
         * settings
         */
        FareSplit split = openSplit();
        // 참여자 셋의 금액은 모두 있고 합도 새 택시비와 같다
        List<FarePayment> payments = List.of(pay(PAYER_ID, 7_900), pay(RIDER_ID, 7_000), pay(LATE_RIDER_ID, 5_000), pay(OUTSIDER_ID, 100));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, payments));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertUnchanged(split);
    }

    @DisplayName("edit - 금액 수가 참여자 수와 같아도 사람이 다르면(보내는 사람 대신 방 밖 사람) PAYMENT_TARGET_MISMATCH")
    @Test
    void edit_paymentForWrongPerson() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, List.of(pay(PAYER_ID, 8_000), pay(RIDER_ID, 7_000), pay(OUTSIDER_ID, 5_000))));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertUnchanged(split);
    }

    @DisplayName("edit - 같은 사람의 금액이 두 번 들어 있으면(수는 맞지만 한 명이 빠짐·수가 하나 많음) PAYMENT_TARGET_MISMATCH 이고 값을 바꾸지 않는다. 금액의 합이 새 택시비와 같아도 마찬가지다")
    @Test
    void edit_duplicatePayment() {

        /*
         * settings
         */
        FareSplit split = openSplit();
        // rider 가 두 번 있고 late rider 가 빠졌다. 수는 참여자 수와 같고 합도 새 택시비와 같다
        List<FarePayment> duplicatedInsteadOfRider = List.of(pay(PAYER_ID, 8_000), pay(RIDER_ID, 7_000), pay(RIDER_ID, 5_000));
        // 참여자 셋이 모두 있고 rider 가 한 번 더 있다. 합은 새 택시비와 같다
        List<FarePayment> duplicatedExtra = List.of(pay(PAYER_ID, 7_900), pay(RIDER_ID, 7_000), pay(LATE_RIDER_ID, 5_000), pay(RIDER_ID, 100));

        /*
         * test
         */
        Throwable sameCount = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, duplicatedInsteadOfRider));
        Throwable oneMore = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, duplicatedExtra));

        /*
         * verify
         */
        assertBusinessException(sameCount, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(oneMore, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertUnchanged(split);
    }

    @DisplayName("edit - 금액을 넣은 순서가 줄 순서와 달라도 줄 순서는 그대로이고 금액은 그 사람을 따라간다")
    @Test
    void edit_paymentOrderIgnored() {

        /*
         * settings
         */
        // 줄 순서 payer → idle rider → rider. userId 순서와도, 금액을 넣은 순서와도 다르다
        FareSplit split = split(FareSplitStatus.OPEN, notSent(IDLE_RIDER_ID), notSent(RIDER_ID));

        /*
         * test
         */
        split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, List.of(pay(RIDER_ID, 8_000), pay(IDLE_RIDER_ID, 5_500), pay(PAYER_ID, 6_500)));

        /*
         * verify
         */
        assertThat(split.participantIds()).containsExactly(PAYER_ID, IDLE_RIDER_ID, RIDER_ID);
        assertThat(split.payments()).containsExactly(pay(PAYER_ID, 6_500), pay(IDLE_RIDER_ID, 5_500), pay(RIDER_ID, 8_000));
        assertThat(split.senderPayments()).containsExactly(pay(IDLE_RIDER_ID, 5_500), pay(RIDER_ID, 8_000));
    }

    @DisplayName("edit - 부담 금액의 합이 새 택시비와 1원이라도 다르면(작음·큼) PAYMENTS_NOT_EQUAL_FARE 이고 값을 바꾸지 않는다. 고치기 전 택시비와는 같아도 마찬가지다")
    @ParameterizedTest(name = "합 18700원, 새 택시비 {0}원")
    @ValueSource(ints = {18_701, 18_699, 30_000, 12_000})
    void edit_paymentsNotEqualFare(int newTotalFare) {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        // 6220 + 6240 × 2 = 18700 은 고치기 전 택시비와 같지만 새 택시비와는 다르다
        Throwable thrown = catchThrowable(() -> split.edit(PAYER_ID, newTotalFare, Bank.SHINHAN, NEW_ACCOUNT, EVEN_PAYMENTS));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.PAYMENTS_NOT_EQUAL_FARE);
        assertUnchanged(split);
    }

    @DisplayName("edit - 누군가 보냈다고 눌렀거나 확인된 송금이 있으면 FARE_SPLIT_ALREADY_STARTED 이고 값을 바꾸지 않는다")
    @ParameterizedTest(name = "sentAt={0}, confirmedAt={1}")
    @CsvSource(value = {"2026-11-03T12:45:00Z, null", "2026-11-03T12:45:00Z, 2026-11-03T13:00:00Z", "null, 2026-11-03T13:00:00Z"}, nullValues = "null")
    void edit_alreadyStarted(Instant sentAt, Instant confirmedAt) {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                notSent(RIDER_ID),
                FareSplitTransfer.load(LATE_RIDER_ID, PAYMENT, sentAt, confirmedAt, 1));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT,
                List.of(pay(PAYER_ID, 8_000), pay(RIDER_ID, 7_000), pay(LATE_RIDER_ID, 5_000))));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_ALREADY_STARTED);
        assertUnchanged(split);
    }

    @DisplayName("edit - 닫힌 정산(COMPLETED·EXPIRED)이면 FARE_SPLIT_CLOSED")
    @ParameterizedTest
    @EnumSource(value = FareSplitStatus.class, names = {"COMPLETED", "EXPIRED"})
    void edit_closed(FareSplitStatus status) {

        /*
         * settings
         */
        Instant confirmedAt = status == FareSplitStatus.COMPLETED ? LATER : null;
        FareSplit split = split(status,
                FareSplitTransfer.load(RIDER_ID, PAYMENT, null, confirmedAt, 1),
                FareSplitTransfer.load(LATE_RIDER_ID, PAYMENT, null, confirmedAt, 1));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT,
                List.of(pay(PAYER_ID, 8_000), pay(RIDER_ID, 7_000), pay(LATE_RIDER_ID, 5_000))));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertThat(split.getTotalFare()).isEqualTo(18_700);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getPayment()).isEqualTo(PAYMENT);
        assertThat(split.amountOf(PAYER_ID)).isEqualTo(PAYER_PAYMENT);
    }

    @DisplayName("edit - 보내는 사람이면 NOT_FARE_SPLIT_PAYER, 정산에 없는 사람이면 NOT_FARE_SPLIT_PARTICIPANT")
    @Test
    void edit_notPayer() {

        /*
         * settings
         */
        FareSplit split = openSplit();
        List<FarePayment> payments = List.of(pay(PAYER_ID, 8_000), pay(RIDER_ID, 7_000), pay(LATE_RIDER_ID, 5_000));

        /*
         * test
         */
        Throwable bySender = catchThrowable(() -> split.edit(RIDER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, payments));
        Throwable byOutsider = catchThrowable(() -> split.edit(OUTSIDER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, payments));

        /*
         * verify
         */
        assertBusinessException(bySender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(byOutsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        assertUnchanged(split);
    }

    @DisplayName("edit - 검사 순서는 받는 사람 → 정산이 열려 있음 → 아무도 안 보냄 → 금액을 넣은 사람이 참여자 전원과 같음 → 금액 합이 택시비와 같음이다")
    @Test
    void edit_validationOrder() {

        /*
         * settings
         */
        // 닫혔고 보낸 송금이 있는 정산
        FareSplit closedStarted = split(FareSplitStatus.EXPIRED, sent(RIDER_ID), notSent(LATE_RIDER_ID));
        // 열려 있고 보낸 송금이 있는 정산
        FareSplit openStarted = split(FareSplitStatus.OPEN, sent(RIDER_ID), notSent(LATE_RIDER_ID));
        // 열려 있고 아무도 안 보낸 정산
        FareSplit notStarted = split(FareSplitStatus.OPEN, notSent(RIDER_ID), notSent(LATE_RIDER_ID));
        // late rider 의 금액이 빠졌고 합도 택시비와 다른 입력
        List<FarePayment> missingAndNotEqual = List.of(pay(PAYER_ID, 20_000), pay(RIDER_ID, 20_000));
        // 참여자 전원의 금액이 있지만 합이 택시비와 다른 입력
        List<FarePayment> notEqual = List.of(pay(PAYER_ID, 20_000), pay(RIDER_ID, 20_000), pay(LATE_RIDER_ID, 1));

        /*
         * test
         */
        Throwable bySender = catchThrowable(() -> closedStarted.edit(RIDER_ID, 18_700, Bank.SHINHAN, NEW_ACCOUNT, missingAndNotEqual));
        Throwable closed = catchThrowable(() -> closedStarted.edit(PAYER_ID, 18_700, Bank.SHINHAN, NEW_ACCOUNT, missingAndNotEqual));
        Throwable started = catchThrowable(() -> openStarted.edit(PAYER_ID, 18_700, Bank.SHINHAN, NEW_ACCOUNT, missingAndNotEqual));
        Throwable targetMismatch = catchThrowable(() -> notStarted.edit(PAYER_ID, 18_700, Bank.SHINHAN, NEW_ACCOUNT, missingAndNotEqual));
        Throwable notEqualFare = catchThrowable(() -> notStarted.edit(PAYER_ID, 18_700, Bank.SHINHAN, NEW_ACCOUNT, notEqual));

        /*
         * verify
         */
        assertBusinessException(bySender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(closed, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertBusinessException(started, FareSplitErrorCode.FARE_SPLIT_ALREADY_STARTED);
        assertBusinessException(targetMismatch, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(notEqualFare, FareSplitErrorCode.PAYMENTS_NOT_EQUAL_FARE);
    }

    // ───────────────────────── remindTransfers ─────────────────────────

    @DisplayName("remindTransfers - 차례가 된(시작 시각 + 요청 횟수 × 4시간, 경계 포함) 아직 안 보낸 사람에게만 보내고 그 사람의 요청 횟수를 1 올린다. SENT·CONFIRMED 와 받는 사람의 줄은 빠진다")
    @Test
    void remindTransfers() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                notSent(RIDER_ID),
                notSent(IDLE_RIDER_ID, 2),
                sent(LATE_RIDER_ID),
                FareSplitTransfer.load(CASH_RIDER_ID, PAYMENT, null, SENT_AT, 1));

        /*
         * test
         */
        FareSplitNotice notice = split.remindTransfers(REMIND_DUE);

        /*
         * verify
         */
        assertThat(notice.shouldSend()).isTrue();
        assertThat(notice.recipientIds()).containsExactly(RIDER_ID);
        assertThat(notice.payments()).containsExactly(pay(RIDER_ID, PAYMENT));
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(2);
        assertThat(split.findTransfer(IDLE_RIDER_ID).orElseThrow().getRequestCount()).as("이미 2번 받아 다음 차례는 8시간").isEqualTo(2);
        assertThat(split.findTransfer(LATE_RIDER_ID).orElseThrow().getRequestCount()).as("보냈다고 누름").isEqualTo(1);
        assertThat(split.findTransfer(CASH_RIDER_ID).orElseThrow().getRequestCount()).as("확인됨").isEqualTo(1);
        assertThat(split.findTransfer(PAYER_ID).orElseThrow().getRequestCount()).as("받는 사람은 요청을 받지 않는다").isZero();
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::status)
                .as("상태는 바꾸지 않는다")
                .containsExactly(TransferStatus.NOT_SENT, TransferStatus.NOT_SENT, TransferStatus.SENT, TransferStatus.CONFIRMED);
    }

    @DisplayName("remindTransfers - 차례가 된 사람이 여럿이면 줄이 담긴 순서대로 보내는 사람 모두에게 보낸다. 받는 사람에게는 보내지 않는다")
    @Test
    void remindTransfers_many() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, notSent(RIDER_ID), notSent(LATE_RIDER_ID), notSent(IDLE_RIDER_ID));

        /*
         * test
         */
        FareSplitNotice notice = split.remindTransfers(REMIND_DUE);

        /*
         * verify
         */
        assertThat(notice.recipientIds()).containsExactly(RIDER_ID, LATE_RIDER_ID, IDLE_RIDER_ID);
        assertThat(notice.payments()).containsExactly(pay(RIDER_ID, PAYMENT), pay(LATE_RIDER_ID, PAYMENT), pay(IDLE_RIDER_ID, PAYMENT));
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::getRequestCount).containsOnly(2);
        assertThat(split.findTransfer(PAYER_ID).orElseThrow().getRequestCount()).as("받는 사람의 줄은 그대로").isZero();
    }

    @DisplayName("remindTransfers - 알림에는 받을 사람마다 그 사람이 보낼 금액을 송금이 담긴 순서로 담는다. 차례가 아닌 사람·보낸 사람의 금액은 담지 않는다")
    @Test
    void remindTransfers_payments() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(IDLE_RIDER_ID, 5_000, null, null, 1),
                FareSplitTransfer.load(LATE_RIDER_ID, 4_000, SENT_AT, null, 1),
                FareSplitTransfer.load(CASH_RIDER_ID, 3_000, null, null, 2),
                FareSplitTransfer.load(RIDER_ID, 6_000, null, null, 1));

        /*
         * test
         */
        FareSplitNotice notice = split.remindTransfers(REMIND_DUE);

        /*
         * verify
         */
        assertThat(notice.shouldSend()).isTrue();
        assertThat(notice.payments()).containsExactly(pay(IDLE_RIDER_ID, 5_000), pay(RIDER_ID, 6_000));
        assertThat(notice.recipientIds()).containsExactly(IDLE_RIDER_ID, RIDER_ID);
        assertThat(split.senderPayments()).as("송금의 금액은 그대로")
                .containsExactly(pay(IDLE_RIDER_ID, 5_000), pay(LATE_RIDER_ID, 4_000), pay(CASH_RIDER_ID, 3_000), pay(RIDER_ID, 6_000));
    }

    @DisplayName("FareSplitNotice - 받은 금액 리스트를 순서 그대로 복사해 담는다. 원본을 바꿔도 알림은 그대로이고 알림의 리스트는 바꿀 수 없다. 비어 있으면 보내지 않는다")
    @Test
    void notice_payments() {

        /*
         * settings
         */
        List<FarePayment> source = new ArrayList<>();
        source.add(pay(IDLE_RIDER_ID, 5_000));
        source.add(pay(RIDER_ID, 6_000));

        /*
         * test
         */
        FareSplitNotice notice = FareSplitNotice.send(source);
        source.add(pay(LATE_RIDER_ID, 4_000));
        Throwable modified = catchThrowable(() -> notice.payments().add(pay(LATE_RIDER_ID, 4_000)));
        FareSplitNotice notDue = FareSplitNotice.notDue();

        /*
         * verify
         */
        assertThat(notice.shouldSend()).isTrue();
        assertThat(notice.payments()).containsExactly(pay(IDLE_RIDER_ID, 5_000), pay(RIDER_ID, 6_000));
        assertThat(notice.recipientIds()).containsExactly(IDLE_RIDER_ID, RIDER_ID);
        assertThat(modified).isInstanceOf(UnsupportedOperationException.class);
        assertThat(notDue.shouldSend()).isFalse();
        assertThat(notDue.payments()).isEmpty();
        assertThat(notDue.recipientIds()).isEmpty();
    }

    @DisplayName("remindTransfers - 첫 차례(시작 4시간 뒤) 전이면 아무것도 바꾸지 않는다")
    @Test
    void remindTransfers_notDue() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, notSent(RIDER_ID));

        /*
         * test
         */
        FareSplitNotice notice = split.remindTransfers(REMIND_DUE.minusSeconds(1));

        /*
         * verify
         */
        assertThat(notice.shouldSend()).isFalse();
        assertThat(notice.recipientIds()).isEmpty();
        assertThat(notice.payments()).isEmpty();
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(1);
    }

    @DisplayName("remindTransfers - 요청을 n 번 받은 사람의 차례는 시작 시각 + n × 4시간이다. 1초 전에는 보내지 않고 그 시각부터 보낸다")
    @ParameterizedTest(name = "요청 {0}번 뒤")
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void remindTransfers_dueBoundary(int requestCount) {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, notSent(RIDER_ID, requestCount));
        Instant dueAt = NOW.plus(FareSplit.REMIND_INTERVAL.multipliedBy(requestCount));

        /*
         * test
         */
        FareSplitNotice before = split.remindTransfers(dueAt.minusSeconds(1));
        int countBefore = split.findTransfer(RIDER_ID).orElseThrow().getRequestCount();
        FareSplitNotice due = split.remindTransfers(dueAt);

        /*
         * verify
         */
        assertThat(before.shouldSend()).isFalse();
        assertThat(countBefore).isEqualTo(requestCount);
        assertThat(due.recipientIds()).containsExactly(RIDER_ID);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(requestCount + 1);
    }

    @DisplayName("remindTransfers - 요청을 6번(상한) 받은 사람에게는 시간이 얼마나 지나도 더 보내지 않는다")
    @Test
    void remindTransfers_maxRequests() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, notSent(RIDER_ID, FareSplit.MAX_TRANSFER_REQUESTS), notSent(LATE_RIDER_ID, 5));
        Instant lastDue = NOW.plus(FareSplit.REMIND_INTERVAL.multipliedBy(5));

        /*
         * test
         */
        FareSplitNotice last = split.remindTransfers(lastDue);
        FareSplitNotice afterMax = split.remindTransfers(NOW.plus(FareSplit.REMIND_INTERVAL.multipliedBy(FareSplit.MAX_TRANSFER_REQUESTS)));
        FareSplitNotice muchLater = split.remindTransfers(NOW.plus(Duration.ofDays(30)));

        /*
         * verify
         */
        assertThat(last.recipientIds()).as("5번 받은 사람만 마지막 재요청을 받는다").containsExactly(LATE_RIDER_ID);
        assertThat(afterMax.shouldSend()).isFalse();
        assertThat(muchLater.shouldSend()).isFalse();
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::getRequestCount).containsOnly(FareSplit.MAX_TRANSFER_REQUESTS);
    }

    @DisplayName("remindTransfers - 1분마다 불러도 시작부터 만료 전까지 4·8·12·16·20시간에 한 번씩, 5번 보내고 요청 횟수는 6이 된다")
    @Test
    void remindTransfers_untilExpiry() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, notSent(RIDER_ID));
        List<Instant> sentAt = new ArrayList<>();

        /*
         * test
         */
        for(Instant now = NOW; now.isBefore(NOW.plus(FareSplit.EXPIRES_AFTER)); now = now.plus(Duration.ofMinutes(1))) {
            if(split.remindTransfers(now).shouldSend()) sentAt.add(now);
        }

        /*
         * verify
         */
        assertThat(sentAt).containsExactly(
                NOW.plus(Duration.ofHours(4)),
                NOW.plus(Duration.ofHours(8)),
                NOW.plus(Duration.ofHours(12)),
                NOW.plus(Duration.ofHours(16)),
                NOW.plus(Duration.ofHours(20)));
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(FareSplit.MAX_TRANSFER_REQUESTS);
    }

    @DisplayName("remindTransfers - 차례가 여러 개 밀렸으면 한 번만 보내고 밀린 차례는 버린다. 다음 요청은 다음 차례에 간다")
    @Test
    void remindTransfers_late() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, notSent(RIDER_ID));
        // 4·8·12시간 차례가 모두 지난 시각
        Instant late = NOW.plus(Duration.ofHours(13));

        /*
         * test
         */
        FareSplitNotice notice = split.remindTransfers(late);
        int countAfterLate = split.findTransfer(RIDER_ID).orElseThrow().getRequestCount();
        FareSplitNotice rightAfter = split.remindTransfers(late.plus(Duration.ofMinutes(30)));
        FareSplitNotice beforeNext = split.remindTransfers(NOW.plus(Duration.ofHours(16)).minusSeconds(1));
        FareSplitNotice next = split.remindTransfers(NOW.plus(Duration.ofHours(16)));

        /*
         * verify
         */
        assertThat(notice.recipientIds()).containsExactly(RIDER_ID);
        assertThat(countAfterLate).as("4·8·12시간 차례를 한 번에 지난 것으로 친다").isEqualTo(4);
        assertThat(rightAfter.shouldSend()).as("밀린 차례를 이어서 보내지 않는다").isFalse();
        assertThat(beforeNext.shouldSend()).isFalse();
        assertThat(next.recipientIds()).as("다음 요청은 시작 16시간 차례").containsExactly(RIDER_ID);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(5);
    }

    @DisplayName("remindTransfers - 못 받았어요로 되돌아온 송금도 차례는 정산 시작 시각 기준이다. 되돌린 시각부터 4시간을 다시 세지 않는다")
    @Test
    void remindTransfers_afterNotReceived() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, sent(RIDER_ID));
        Instant notReceivedAt = NOW.plus(Duration.ofHours(5));
        FareSplitNotice whileSent = split.remindTransfers(notReceivedAt);
        split.markNotReceived(PAYER_ID, RIDER_ID);

        /*
         * test
         */
        FareSplitNotice overdue = split.remindTransfers(notReceivedAt.plus(Duration.ofMinutes(1)));
        FareSplitNotice beforeNext = split.remindTransfers(NOW.plus(Duration.ofHours(8)).minusSeconds(1));
        FareSplitNotice next = split.remindTransfers(NOW.plus(Duration.ofHours(8)));

        /*
         * verify
         */
        assertThat(whileSent.shouldSend()).as("보냈다고 누른 동안에는 재요청하지 않는다").isFalse();
        assertThat(overdue.recipientIds()).as("시작 4시간 차례가 이미 지나 바로 보낸다").containsExactly(RIDER_ID);
        assertThat(beforeNext.shouldSend()).isFalse();
        assertThat(next.recipientIds()).as("다음 차례는 되돌린 시각 + 4시간이 아니라 시작 + 8시간").containsExactly(RIDER_ID);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(3);
    }

    @DisplayName("remindTransfers - 모두 보냈거나 확인됐으면 차례가 지나도 받을 사람이 없어 보내지 않는다")
    @Test
    void remindTransfers_nobodyLeft() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                sent(RIDER_ID),
                FareSplitTransfer.load(LATE_RIDER_ID, PAYMENT, SENT_AT, LATER, 1));

        /*
         * test
         */
        FareSplitNotice notice = split.remindTransfers(REMIND_DUE);

        /*
         * verify
         */
        assertThat(notice.shouldSend()).isFalse();
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::getRequestCount).containsOnly(1);
    }

    @DisplayName("remindTransfers - 닫힌 정산이나 받을 계좌가 지워진(받는 사람 탈퇴) 정산은 재요청하지 않고 요청 횟수도 그대로다")
    @Test
    void remindTransfers_closedOrNoAccount() {

        /*
         * settings
         */
        FareSplit expired = split(FareSplitStatus.EXPIRED, notSent(RIDER_ID));
        FareSplit noAccount = loadSplit(FareSplitStatus.OPEN, null, null, notSent(RIDER_ID));
        FareSplit forgotten = split(FareSplitStatus.OPEN, notSent(RIDER_ID));
        forgotten.forgetAccount();

        /*
         * test
         */
        FareSplitNotice closedNotice = expired.remindTransfers(REMIND_DUE);
        FareSplitNotice noAccountNotice = noAccount.remindTransfers(REMIND_DUE);
        FareSplitNotice forgottenNotice = forgotten.remindTransfers(REMIND_DUE);

        /*
         * verify
         */
        assertThat(closedNotice.shouldSend()).isFalse();
        assertThat(noAccountNotice.shouldSend()).isFalse();
        assertThat(forgottenNotice.shouldSend()).isFalse();
        assertThat(expired.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(1);
        assertThat(noAccount.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(1);
        assertThat(forgotten.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(1);
    }

    // ───────────────────────── expire ─────────────────────────

    @DisplayName("expire - 시작하고 1일이 지나면 EXPIRED 로 닫고 닫힌 시각을 남기며 계좌번호를 지운다")
    @Test
    void expire() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, notSent(RIDER_ID));
        Instant now = NOW.plus(Duration.ofDays(1));

        /*
         * test
         */
        boolean expired = split.expire(now);

        /*
         * verify
         */
        assertThat(expired).isTrue();
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.EXPIRED);
        assertThat(split.getClosedAt()).isEqualTo(now);
        assertThat(split.getAccountNumber()).isNull();
        assertThat(split.receivingAccount()).isEmpty();
    }

    @DisplayName("expire - 1일이 지나기 전이면 그대로 둔다")
    @Test
    void expire_notYet() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, notSent(RIDER_ID));

        /*
         * test
         */
        boolean expired = split.expire(NOW.plus(Duration.ofDays(1)).minusSeconds(1));

        /*
         * verify
         */
        assertThat(expired).isFalse();
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(split.getClosedAt()).isNull();
        assertThat(split.getAccountNumber()).isEqualTo(ACCOUNT);
    }

    @DisplayName("expire - 이미 닫힌 정산(COMPLETED·EXPIRED)은 닫힌 시각을 바꾸지 않는다")
    @ParameterizedTest
    @EnumSource(value = FareSplitStatus.class, names = {"COMPLETED", "EXPIRED"})
    void expire_closed(FareSplitStatus status) {

        /*
         * settings
         */
        FareSplit split = split(status, FareSplitTransfer.load(RIDER_ID, PAYMENT, SENT_AT, LATER, 1));

        /*
         * test
         */
        boolean expired = split.expire(NOW.plus(Duration.ofDays(2)));

        /*
         * verify
         */
        assertThat(expired).isFalse();
        assertThat(split.getStatus()).isEqualTo(status);
        assertThat(split.getClosedAt()).isEqualTo(LATER);
    }

    // ───────────────────────── forgetAccount ─────────────────────────

    @DisplayName("forgetAccount - 계좌번호를 지우고 true. 정산은 OPEN 으로 남고 계좌를 보여 주지 않는다")
    @Test
    void forgetAccount() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        boolean cleared = split.forgetAccount();

        /*
         * verify
         */
        assertThat(cleared).isTrue();
        assertThat(split.getAccountNumber()).isNull();
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(split.receivingAccount()).isEmpty();
    }

    @DisplayName("forgetAccount - 이미 지웠으면(닫힌 정산 포함) false")
    @Test
    void forgetAccount_alreadyCleared() {

        /*
         * settings
         */
        FareSplit split = openSplit();
        split.forgetAccount();
        FareSplit completed = split(FareSplitStatus.COMPLETED, FareSplitTransfer.load(RIDER_ID, PAYMENT, SENT_AT, LATER, 1));

        /*
         * test
         */
        boolean again = split.forgetAccount();
        boolean closed = completed.forgetAccount();

        /*
         * verify
         */
        assertThat(again).isFalse();
        assertThat(closed).isFalse();
    }

    // ───────────────────────── fixtures ─────────────────────────

    /** 택시비 18,700원, 보내는 사람 둘에게 6,240원씩·받는 사람에게 6,220원을 넣어 NOW 에 시작한 정산 */
    private static FareSplit openSplit() {
        return FareSplit.create(threeRiders(), PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, EVEN_PAYMENTS, NOW);
    }

    /** NOW 에 시작한 정산을 보내는 사람들의 줄(senderTransfers)로 불러온다. 닫힌 정산은 LATER 에 닫혀 계좌가 지워져 있다 */
    private static FareSplit split(FareSplitStatus status, FareSplitTransfer... senderTransfers) {
        boolean open = status == FareSplitStatus.OPEN;

        return loadSplit(status, open ? ACCOUNT : null, open ? null : LATER, senderTransfers);
    }

    /**
     *  NOW 에 시작한 정산을 불러온다. 장부 맨 앞에 받는 사람의 줄(6,220원, 시작할 때 CONFIRMED, 요청 횟수 0)을 넣고 보내는 사람들의 줄을 잇는다.
     *  택시비는 줄에 담긴 부담 금액의 합이다 - 보내는 사람 둘이 6,240원씩이면 18,700원
     */
    private static FareSplit loadSplit(FareSplitStatus status, AccountNumber accountNumber, Instant closedAt, FareSplitTransfer... senderTransfers) {
        List<FareSplitTransfer> transfers = new ArrayList<>();
        transfers.add(FareSplitTransfer.load(PAYER_ID, PAYER_PAYMENT, NOW, NOW, 0));
        transfers.addAll(List.of(senderTransfers));
        int totalFare = transfers.stream().mapToInt(FareSplitTransfer::getPayment).sum();

        return FareSplit.load(PARTY_ID, PAYER_ID, totalFare, Bank.KAKAO, accountNumber, status, NOW, closedAt, transfers);
    }

    /** memberId 가 amount 원을 부담한다 */
    private static FarePayment pay(Long memberId, int amount) {
        return new FarePayment(memberId, amount);
    }

    /** 시작할 때 첫 송금 요청만 받고 아직 안 보낸 송금 */
    private static FareSplitTransfer notSent(Long memberId) {
        return notSent(memberId, 1);
    }

    /** 송금 요청을 requestCount 번 받고 아직 안 보낸 송금 */
    private static FareSplitTransfer notSent(Long memberId, int requestCount) {
        return FareSplitTransfer.load(memberId, PAYMENT, null, null, requestCount);
    }

    /** 첫 송금 요청을 받고 SENT_AT 에 보냈다고 누른 송금 */
    private static FareSplitTransfer sent(Long memberId) {
        return FareSplitTransfer.load(memberId, PAYMENT, SENT_AT, null, 1);
    }

    /** 셋 다 도착해 탄 방. 방장은 payer, 합류 순 payer → rider → late rider */
    private static RideRoster threeRiders() {
        return finished(PAYER_ID, List.of(PAYER_ID, RIDER_ID, LATE_RIDER_ID), List.of(PAYER_ID, RIDER_ID, LATE_RIDER_ID));
    }

    /** 방장은 payer. 합류 순 payer(도착) → 도착하지 않은 멤버 → rider(도착). 도착한 멤버만 탑승자다(matching 이 정한다) */
    private static RideRoster withAbsent() {
        return finished(PAYER_ID, List.of(PAYER_ID, ABSENT_ID, RIDER_ID), List.of(PAYER_ID, RIDER_ID));
    }

    /** 동승이 끝난 방. 멤버·탑승자 모두 합류 순 */
    private static RideRoster finished(Long hostId, List<Long> memberIds, List<Long> riderIds) {
        return new RideRoster(PARTY_ID, hostId, true, memberIds, riderIds);
    }

    /** 택시비 18,700원·카카오 계좌·보내는 사람마다 6,240원·받는 사람 6,220원으로 시작한 정산이 고쳐지지 않았는지 */
    private static void assertUnchanged(FareSplit split) {
        assertThat(split.getTotalFare()).isEqualTo(18_700);
        assertThat(split.getBank()).isEqualTo(Bank.KAKAO);
        assertThat(split.getAccountNumber()).isEqualTo(ACCOUNT);
        assertThat(split.senderTransfers()).extracting(FareSplitTransfer::getPayment).containsOnly(PAYMENT);
        assertThat(split.amountOf(PAYER_ID)).isEqualTo(PAYER_PAYMENT);
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
