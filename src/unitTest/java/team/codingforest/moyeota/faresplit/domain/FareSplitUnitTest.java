package team.codingforest.moyeota.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  정산 시작 규칙(탑승자·payer 자격, 택시비 범위), 1인당 금액(10원 단위 올림)과 payer 몫, 역할·계좌·딥링크 노출 규칙,
 *  송금 처리(보냄·받음·못 받음)와 완료, 수정·취소 조건, 송금 재요청·입금 확인 요청 차례, 만료, 받는 사람 탈퇴 시 계좌 삭제
 */
class FareSplitUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long PAYER_ID = 1L;
    private static final Long RIDER_ID = 2L;
    private static final Long LATE_RIDER_ID = 3L;
    private static final Long NO_SHOW_ID = 4L;
    private static final Long VOIDED_NO_SHOW_ID = 5L;
    private static final Long CASH_RIDER_ID = 6L;
    private static final Long IDLE_RIDER_ID = 7L;
    private static final Long OUTSIDER_ID = 99L;
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant NOW = MEETING_AT.plus(Duration.ofMinutes(40));
    private static final Instant SENT_AT = NOW.plus(Duration.ofMinutes(5));
    private static final Instant LATER = NOW.plus(Duration.ofHours(2));
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");
    private static final AccountNumber NEW_ACCOUNT = new AccountNumber("110123456789");
    // NOW(한국 시각 11-03 21:40)에 시작한 정산의 재요청 차례 - 4시간마다. 2·3번째(11-04 01:40·05:40)는 방해 금지 시간이고 4번째(09:40)부터 낮이다
    private static final Instant SECOND_DUE = NOW.plus(Duration.ofHours(4));
    private static final Instant THIRD_DUE = NOW.plus(Duration.ofHours(8));
    private static final Instant FOURTH_DUE = NOW.plus(Duration.ofHours(12));
    private static final Instant LAST_DUE = NOW.plus(Duration.ofHours(20));
    // 한국 시각 11-04 08:00 - 방해 금지 시간이 끝난 때
    private static final Instant MORNING = Instant.parse("2026-11-03T23:00:00Z");
    // 한국 시각 11-04 10:00 - 만료(21:40) 전 낮
    private static final Instant DAYTIME = Instant.parse("2026-11-04T01:00:00Z");

    // ───────────────────────── open ─────────────────────────

    @DisplayName("open - payer 를 뺀 탑승자마다 NOT_SENT 송금을 합류 순으로 만들고 1인당 금액은 탑승자 수로 나눠 10원 단위로 올린다. 첫 송금 요청을 1번째 차례로 센다")
    @Test
    void open() {

        /*
         * settings
         */
        RideRoster party = threeRiders();

        /*
         * test
         */
        FareSplit split = FareSplit.open(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, NOW);

        /*
         * verify
         */
        assertThat(split.getPartyId()).isEqualTo(PARTY_ID);
        assertThat(split.getPayerId()).isEqualTo(PAYER_ID);
        assertThat(split.getTotalFare()).isEqualTo(18_700);
        assertThat(split.getAmountPerPerson()).as("18700 / 3 = 6233.3 → 10원 단위로 올림").isEqualTo(6_240);
        assertThat(split.payerShare()).isEqualTo(18_700 - 6_240 * 2);
        assertThat(split.getBank()).isEqualTo(Bank.KAKAO);
        assertThat(split.getAccountNumber()).isEqualTo(ACCOUNT);
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(split.getCreatedAt()).isEqualTo(NOW);
        assertThat(split.getClosedAt()).isNull();
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(RIDER_ID, LATE_RIDER_ID);
        assertThat(split.getTransfers()).allSatisfy(t -> {
            assertThat(t.status()).isEqualTo(TransferStatus.NOT_SENT);
            assertThat(t.getRequestCount()).isEqualTo(1);
            assertThat(t.getLastRequestedAt()).isEqualTo(NOW);
            assertThat(t.getConfirmRequestedAt()).isNull();
        });
    }

    @DisplayName("open - 노쇼 멤버는 탑승자가 아니라 송금 대상에서 빠지고 N 에도 세지 않는다")
    @Test
    void open_excludesNoShow() {

        /*
         * settings
         */
        RideRoster party = withNoShow();

        /*
         * test
         */
        FareSplit split = FareSplit.open(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, NOW);

        /*
         * verify
         */
        assertThat(split.senderIds()).containsExactly(RIDER_ID);
        assertThat(split.getAmountPerPerson()).as("18700 / 2").isEqualTo(9_350);
        assertThat(split.payerShare()).isEqualTo(9_350);
    }

    @DisplayName("open - 노쇼가 무효로 돌아갔어도 도착을 누르지 않은 멤버는 탑승자가 아니다")
    @Test
    void open_excludesVoidedNoShow() {

        /*
         * settings
         */
        RideRoster party = withVoidedNoShow();

        /*
         * test
         */
        FareSplit split = FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, NOW);

        /*
         * verify
         */
        assertThat(split.senderIds()).containsExactly(RIDER_ID);
    }

    @DisplayName("open - payer 가 합류 순 중간이어도 나머지 탑승자를 합류 순으로 담는다")
    @Test
    void open_payerInMiddle() {

        /*
         * settings
         */
        RideRoster party = threeRiders();

        /*
         * test
         */
        FareSplit split = FareSplit.open(party, RIDER_ID, 10_000, Bank.KB, ACCOUNT, NOW);

        /*
         * verify
         */
        assertThat(split.senderIds()).containsExactly(PAYER_ID, LATE_RIDER_ID);
        assertThat(split.participantIds()).containsExactly(RIDER_ID, PAYER_ID, LATE_RIDER_ID);
    }

    @DisplayName("open - 방의 멤버가 아니면 NOT_PARTY_MEMBER")
    @Test
    void open_notMember() {

        /*
         * settings
         */
        RideRoster party = threeRiders();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, OUTSIDER_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_PARTY_MEMBER);
    }

    @DisplayName("open - 동승이 끝나지 않은(FINISHED 가 아닌) 방이면 PARTY_NOT_FINISHED")
    @Test
    void open_notFinished() {

        /*
         * settings
         */
        RideRoster party = new RideRoster(PARTY_ID, false, List.of(PAYER_ID, RIDER_ID), List.of(PAYER_ID, RIDER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.PARTY_NOT_FINISHED);
    }

    @DisplayName("open - 노쇼로 찍힌 멤버는 payer 가 될 수 없어 NOT_A_RIDER")
    @Test
    void open_payerNoShow() {

        /*
         * settings
         */
        RideRoster party = withNoShow();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, NO_SHOW_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_A_RIDER);
    }

    @DisplayName("open - 노쇼가 무효로 돌아갔어도 도착을 누르지 않은 멤버는 payer 가 될 수 없어 NOT_A_RIDER")
    @Test
    void open_payerVoidedNoShow() {

        /*
         * settings
         */
        RideRoster party = withVoidedNoShow();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, VOIDED_NO_SHOW_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_A_RIDER);
    }

    @DisplayName("open - 도착 없이 끝난 방(약속을 정하지 않은 구버전 종료)은 아무도 탑승자가 아니라 NOT_A_RIDER")
    @Test
    void open_noArrivals() {

        /*
         * settings
         */
        RideRoster party = finished(List.of(PAYER_ID, RIDER_ID), List.of());

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_A_RIDER);
    }

    @DisplayName("open - 함께 탄 사람이 payer 혼자면 NOT_ENOUGH_RIDERS")
    @Test
    void open_aloneRider() {

        /*
         * settings
         */
        RideRoster party = finished(List.of(PAYER_ID, NO_SHOW_ID), List.of(PAYER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_ENOUGH_RIDERS);
    }

    @DisplayName("open - 택시비가 1,000~500,000원 밖이면 INVALID_FARE")
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 999, 500_001, Integer.MAX_VALUE})
    void open_invalidFare(int totalFare) {

        /*
         * settings
         */
        RideRoster party = threeRiders();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, PAYER_ID, totalFare, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.INVALID_FARE);
    }

    @DisplayName("open - 택시비 경계값 1,000원과 500,000원은 받는다")
    @ParameterizedTest
    @ValueSource(ints = {FareSplit.MIN_TOTAL_FARE, FareSplit.MAX_TOTAL_FARE})
    void open_fareBoundary(int totalFare) {

        /*
         * settings
         */
        RideRoster party = threeRiders();

        /*
         * test
         */
        FareSplit split = FareSplit.open(party, PAYER_ID, totalFare, Bank.KB, ACCOUNT, NOW);

        /*
         * verify
         */
        assertThat(split.getTotalFare()).isEqualTo(totalFare);
        assertThat(split.payerShare()).isPositive();
    }

    // ───────────────────────── 1인당 금액·payer 몫 ─────────────────────────

    @DisplayName("amountPerPerson - 택시비 / 탑승자 수를 10원 단위로 올리고 payerShare 는 택시비에서 보내는 사람 몫을 뺀 값이다")
    @ParameterizedTest(name = "{0}원 / {1}명 → 1인당 {2}원, payer {3}원")
    @CsvSource({
            "10000, 2, 5000, 5000",
            "10001, 2, 5010, 4991",
            "18700, 3, 6240, 6220",
            "1000, 3, 340, 320",
            "12345, 3, 4120, 4105",
            "500000, 3, 166670, 166660"})
    void amountPerPerson(int totalFare, int riders, int expectedPerPerson, int expectedPayerShare) {

        /*
         * settings
         */
        List<FareSplitTransfer> transfers = java.util.stream.LongStream.rangeClosed(2, riders)
                .mapToObj(id -> FareSplitTransfer.load(id, null, null, 0, null, null))
                .toList();

        /*
         * test
         */
        int perPerson = FareSplit.amountPerPerson(totalFare, riders);
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, totalFare, perPerson, Bank.KB, ACCOUNT, FareSplitStatus.OPEN, NOW, null, transfers);

        /*
         * verify
         */
        assertThat(perPerson).isEqualTo(expectedPerPerson);
        assertThat(split.payerShare()).isEqualTo(expectedPayerShare);
        assertThat(perPerson * (riders - 1) + split.payerShare()).as("나눈 금액의 합은 택시비").isEqualTo(totalFare);
    }

    // ───────────────────────── 역할·내 몫 ─────────────────────────

    @DisplayName("roleOf - payer 는 PAYER, 송금 행이 있는 사람은 SENDER")
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

    @DisplayName("roleOf - 정산에 없는 사람(노쇼 멤버·방 밖)은 NOT_FARE_SPLIT_PARTICIPANT")
    @ParameterizedTest
    @ValueSource(longs = {4L, 99L})
    void roleOf_notParticipant(Long memberId) {

        /*
         * settings
         */
        FareSplit split = FareSplit.open(withNoShow(), PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, NOW);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.roleOf(memberId));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    @DisplayName("amountOf - payer 는 payerShare, 보내는 사람은 1인당 금액")
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
        assertThat(payerAmount).isEqualTo(6_220);
        assertThat(senderAmount).isEqualTo(6_240);
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

    // ───────────────────────── 계좌·딥링크 노출 ─────────────────────────

    @DisplayName("receivingAccount / tossDeeplink - OPEN 이면 계좌와 1인당 금액으로 만든 딥링크를 준다")
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
        var deeplink = split.tossDeeplink();

        /*
         * verify
         */
        assertThat(account).contains(ACCOUNT);
        assertThat(deeplink).contains(TossDeeplink.of(6_240, Bank.KAKAO, ACCOUNT));
    }

    @DisplayName("receivingAccount / tossDeeplink - 닫힌 정산(COMPLETED·EXPIRED)은 계좌가 남아 있어도 보여 주지 않는다")
    @ParameterizedTest
    @EnumSource(value = FareSplitStatus.class, names = {"COMPLETED", "EXPIRED"})
    void receivingAccount_closed(FareSplitStatus status) {

        /*
         * settings
         */
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, 6_240, Bank.KAKAO, ACCOUNT, status, NOW, NOW.plusSeconds(60),
                List.of(FareSplitTransfer.load(RIDER_ID, null, null, 0, null, null)));

        /*
         * test
         */
        var account = split.receivingAccount();
        var deeplink = split.tossDeeplink();

        /*
         * verify
         */
        assertThat(account).isEmpty();
        assertThat(deeplink).isEmpty();
    }

    @DisplayName("receivingAccount / tossDeeplink - OPEN 이어도 계좌가 지워졌으면(null) 없다")
    @Test
    void receivingAccount_cleared() {

        /*
         * settings
         */
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, 6_240, Bank.KAKAO, null, FareSplitStatus.OPEN, NOW, null,
                List.of(FareSplitTransfer.load(RIDER_ID, null, null, 0, null, null)));

        /*
         * test
         */
        var account = split.receivingAccount();
        var deeplink = split.tossDeeplink();

        /*
         * verify
         */
        assertThat(account).isEmpty();
        assertThat(deeplink).isEmpty();
    }

    // ───────────────────────── 송금 순서 ─────────────────────────

    @DisplayName("transfersInJoinOrder - 송금을 넘긴 합류 순으로 담고, 합류 순에 없는 사람은 맨 뒤에 userId 순으로 둔다. 정산이 담은 순서는 그대로다")
    @Test
    void transfersInJoinOrder() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, notSent(IDLE_RIDER_ID), notSent(LATE_RIDER_ID), notSent(CASH_RIDER_ID), notSent(RIDER_ID));

        /*
         * test
         */
        List<FareSplitTransfer> ordered = split.transfersInJoinOrder(List.of(CASH_RIDER_ID, PAYER_ID, RIDER_ID));

        /*
         * verify
         */
        assertThat(ordered).extracting(FareSplitTransfer::getMemberId).containsExactly(CASH_RIDER_ID, RIDER_ID, LATE_RIDER_ID, IDLE_RIDER_ID);
        assertThat(split.senderIds()).containsExactly(IDLE_RIDER_ID, LATE_RIDER_ID, CASH_RIDER_ID, RIDER_ID);
    }

    @DisplayName("transfersInJoinOrder - 합류 순을 모르면(빈 목록 - 방이 없음) userId 순이다")
    @Test
    void transfersInJoinOrder_unknown() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, notSent(LATE_RIDER_ID), notSent(IDLE_RIDER_ID), notSent(RIDER_ID));

        /*
         * test
         */
        List<FareSplitTransfer> ordered = split.transfersInJoinOrder(List.of());

        /*
         * verify
         */
        assertThat(ordered).extracting(FareSplitTransfer::getMemberId).containsExactly(RIDER_ID, LATE_RIDER_ID, IDLE_RIDER_ID);
    }

    // ───────────────────────── markSent ─────────────────────────

    @DisplayName("markSent - 아직 안 보낸 보내는 사람이 누르면 SENT 가 되고 보낸 시각을 남긴다")
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
        assertThat(split.findTransfer(LATE_RIDER_ID).orElseThrow().status()).as("다른 사람 송금은 그대로").isEqualTo(TransferStatus.NOT_SENT);
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
    }

    @DisplayName("markSent - 이미 SENT·CONFIRMED 면 시각을 바꾸지 않고 false(다시 눌러도 된다). 만료된 정산이어도 마찬가지다")
    @ParameterizedTest
    @EnumSource(value = FareSplitStatus.class, names = {"OPEN", "EXPIRED"})
    void markSent_alreadySent(FareSplitStatus status) {

        /*
         * settings
         */
        FareSplit split = split(status,
                FareSplitTransfer.load(RIDER_ID, SENT_AT, null, 1, NOW, null),
                FareSplitTransfer.load(LATE_RIDER_ID, null, SENT_AT, 1, NOW, null));

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

    @DisplayName("markSent - 보내기 전에 닫힌(EXPIRED) 정산이면 FARE_SPLIT_CLOSED 이고 바꾸지 않는다")
    @Test
    void markSent_closed() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.EXPIRED, FareSplitTransfer.load(RIDER_ID, null, null, 3, NOW, null));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.markSent(RIDER_ID, LATER));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().status()).isEqualTo(TransferStatus.NOT_SENT);
    }

    // ───────────────────────── confirmReceived ─────────────────────────

    @DisplayName("confirmReceived - SENT 송금을 확인하면 CONFIRMED 가 되고, 남은 송금이 있으면 정산은 OPEN 이고 계좌도 남는다")
    @Test
    void confirmReceived() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, SENT_AT, null, 1, NOW, null),
                FareSplitTransfer.load(LATE_RIDER_ID, null, null, 1, NOW, null));

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

    @DisplayName("confirmReceived - 마지막 송금을 확인하면 COMPLETED 로 닫고 닫힌 시각을 남기며 계좌번호를 지운다")
    @Test
    void confirmReceived_completes() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, SENT_AT, SENT_AT.plusSeconds(60), 1, NOW, null),
                FareSplitTransfer.load(LATE_RIDER_ID, SENT_AT, null, 1, NOW, SENT_AT.plus(Duration.ofDays(1))));

        /*
         * test
         */
        boolean changed = split.confirmReceived(PAYER_ID, LATE_RIDER_ID, LATER);

        /*
         * verify
         */
        assertThat(changed).isTrue();
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.COMPLETED);
        assertThat(split.getClosedAt()).isEqualTo(LATER);
        assertThat(split.getAccountNumber()).isNull();
        assertThat(split.receivingAccount()).isEmpty();
        assertThat(split.tossDeeplink()).isEmpty();
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::status).containsOnly(TransferStatus.CONFIRMED);
    }

    @DisplayName("confirmReceived - 이미 CONFIRMED 면 바꾸지 않고 false. 이미 COMPLETED 인 정산에서 다시 눌러도 예외 없이 그대로다")
    @Test
    void confirmReceived_alreadyConfirmed() {

        /*
         * settings
         */
        Instant closedAt = SENT_AT.plusSeconds(60);
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, 6_240, Bank.KAKAO, null, FareSplitStatus.COMPLETED, NOW, closedAt,
                List.of(FareSplitTransfer.load(RIDER_ID, SENT_AT, closedAt, 1, NOW, null),
                        FareSplitTransfer.load(LATE_RIDER_ID, null, closedAt, 1, NOW, null)));

        /*
         * test
         */
        boolean changed = split.confirmReceived(PAYER_ID, RIDER_ID, LATER);

        /*
         * verify
         */
        assertThat(changed).isFalse();
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getConfirmedAt()).isEqualTo(closedAt);
        assertThat(split.getClosedAt()).isEqualTo(closedAt);
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
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::status).containsOnly(TransferStatus.NOT_SENT);
    }

    @DisplayName("confirmReceived - 이 정산의 보내는 사람이 아닌 사람(받는 사람 자신·방 밖)의 송금이면 FARE_SPLIT_TRANSFER_NOT_FOUND")
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
    }

    @DisplayName("confirmReceived - 확인 전에 닫힌(EXPIRED) 정산이면 FARE_SPLIT_CLOSED")
    @Test
    void confirmReceived_closed() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.EXPIRED, FareSplitTransfer.load(RIDER_ID, SENT_AT, null, 1, NOW, null));

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

    // ───────────────────────── markNotReceived ─────────────────────────

    @DisplayName("markNotReceived - SENT 송금을 NOT_SENT 로 되돌리고 보낸 시각·입금 확인 요청 시각을 지운다. 송금 요청 횟수는 남긴다")
    @Test
    void markNotReceived() {

        /*
         * settings
         */
        Instant confirmRequestedAt = SENT_AT.plus(Duration.ofDays(1));
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, SENT_AT, null, 2, NOW, confirmRequestedAt),
                FareSplitTransfer.load(LATE_RIDER_ID, SENT_AT, null, 1, NOW, null));

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
        assertThat(transfer.getConfirmRequestedAt()).isNull();
        assertThat(transfer.getRequestCount()).isEqualTo(2);
        assertThat(transfer.getLastRequestedAt()).isEqualTo(NOW);
        assertThat(split.findTransfer(LATE_RIDER_ID).orElseThrow().status()).isEqualTo(TransferStatus.SENT);
    }

    @DisplayName("markNotReceived - SENT 가 아닌 송금(아직 안 보냄·이미 확인)이면 TRANSFER_NOT_SENT")
    @Test
    void markNotReceived_notSent() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, null, null, 1, NOW, null),
                FareSplitTransfer.load(LATE_RIDER_ID, SENT_AT, LATER, 1, NOW, null));

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

    @DisplayName("markNotReceived - 보내는 사람이 누르면 NOT_FARE_SPLIT_PAYER")
    @Test
    void markNotReceived_notPayer() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, FareSplitTransfer.load(RIDER_ID, SENT_AT, null, 1, NOW, null));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.markNotReceived(RIDER_ID, RIDER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().status()).isEqualTo(TransferStatus.SENT);
    }

    @DisplayName("markNotReceived - 닫힌(EXPIRED) 정산이면 SENT 송금이어도 FARE_SPLIT_CLOSED")
    @Test
    void markNotReceived_closed() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.EXPIRED, FareSplitTransfer.load(RIDER_ID, SENT_AT, null, 1, NOW, null));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.markNotReceived(PAYER_ID, RIDER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_CLOSED);
    }

    // ───────────────────────── edit ─────────────────────────

    @DisplayName("edit - 아무도 보내기 전이면 택시비·은행·계좌를 바꾸고 1인당 금액을 탑승자 수(보내는 사람 + 1)로 다시 계산한다")
    @Test
    void edit() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT);

        /*
         * verify
         */
        assertThat(split.getTotalFare()).isEqualTo(20_000);
        assertThat(split.getAmountPerPerson()).as("20000 / 3 = 6666.7 → 6670").isEqualTo(6_670);
        assertThat(split.payerShare()).isEqualTo(20_000 - 6_670 * 2);
        assertThat(split.getBank()).isEqualTo(Bank.SHINHAN);
        assertThat(split.getAccountNumber()).isEqualTo(NEW_ACCOUNT);
        assertThat(split.tossDeeplink()).contains(TossDeeplink.of(6_670, Bank.SHINHAN, NEW_ACCOUNT));
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(split.getCreatedAt()).isEqualTo(NOW);
    }

    @DisplayName("edit - 보내는 사람이 한 명이면 둘로 나눈다(노쇼 멤버는 N 에 세지 않는다)")
    @Test
    void edit_twoRiders() {

        /*
         * settings
         */
        FareSplit split = FareSplit.open(withNoShow(), PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, NOW);

        /*
         * test
         */
        split.edit(PAYER_ID, 10_001, Bank.KAKAO, ACCOUNT);

        /*
         * verify
         */
        assertThat(split.getAmountPerPerson()).isEqualTo(5_010);
        assertThat(split.payerShare()).isEqualTo(4_991);
    }

    @DisplayName("edit / ensureCancelable - 누군가 보냈다고 눌렀거나 확인된 송금이 있으면 FARE_SPLIT_ALREADY_STARTED 이고 값을 바꾸지 않는다")
    @ParameterizedTest(name = "sentAt={0}, confirmedAt={1}")
    @CsvSource(value = {"2026-11-03T12:45:00Z, null", "2026-11-03T12:45:00Z, 2026-11-03T13:00:00Z", "null, 2026-11-03T13:00:00Z"}, nullValues = "null")
    void edit_alreadyStarted(Instant sentAt, Instant confirmedAt) {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, null, null, 1, NOW, null),
                FareSplitTransfer.load(LATE_RIDER_ID, sentAt, confirmedAt, 1, NOW, null));

        /*
         * test
         */
        Throwable edited = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT));
        Throwable canceled = catchThrowable(() -> split.ensureCancelable(PAYER_ID));

        /*
         * verify
         */
        assertBusinessException(edited, FareSplitErrorCode.FARE_SPLIT_ALREADY_STARTED);
        assertBusinessException(canceled, FareSplitErrorCode.FARE_SPLIT_ALREADY_STARTED);
        assertThat(split.getTotalFare()).isEqualTo(18_700);
        assertThat(split.getAmountPerPerson()).isEqualTo(6_240);
        assertThat(split.getBank()).isEqualTo(Bank.KAKAO);
        assertThat(split.getAccountNumber()).isEqualTo(ACCOUNT);
    }

    @DisplayName("edit / ensureCancelable - 닫힌 정산(COMPLETED·EXPIRED)이면 FARE_SPLIT_CLOSED")
    @ParameterizedTest
    @EnumSource(value = FareSplitStatus.class, names = {"COMPLETED", "EXPIRED"})
    void edit_closed(FareSplitStatus status) {

        /*
         * settings
         */
        Instant confirmedAt = status == FareSplitStatus.COMPLETED ? LATER : null;
        FareSplit split = split(status, FareSplitTransfer.load(RIDER_ID, null, confirmedAt, 3, NOW, null));

        /*
         * test
         */
        Throwable edited = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT));
        Throwable canceled = catchThrowable(() -> split.ensureCancelable(PAYER_ID));

        /*
         * verify
         */
        assertBusinessException(edited, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertBusinessException(canceled, FareSplitErrorCode.FARE_SPLIT_CLOSED);
    }

    @DisplayName("edit / ensureCancelable - 보내는 사람이면 NOT_FARE_SPLIT_PAYER, 정산에 없는 사람이면 NOT_FARE_SPLIT_PARTICIPANT")
    @Test
    void edit_notPayer() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable editedBySender = catchThrowable(() -> split.edit(RIDER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT));
        Throwable canceledBySender = catchThrowable(() -> split.ensureCancelable(RIDER_ID));
        Throwable editedByOutsider = catchThrowable(() -> split.edit(OUTSIDER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT));
        Throwable canceledByOutsider = catchThrowable(() -> split.ensureCancelable(OUTSIDER_ID));

        /*
         * verify
         */
        assertBusinessException(editedBySender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(canceledBySender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(editedByOutsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        assertBusinessException(canceledByOutsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        assertThat(split.getTotalFare()).isEqualTo(18_700);
    }

    @DisplayName("edit - 택시비가 1,000~500,000원 밖이면 INVALID_FARE 이고 값을 바꾸지 않는다")
    @ParameterizedTest
    @ValueSource(ints = {0, 999, 500_001})
    void edit_invalidFare(int totalFare) {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.edit(PAYER_ID, totalFare, Bank.SHINHAN, NEW_ACCOUNT));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.INVALID_FARE);
        assertThat(split.getTotalFare()).isEqualTo(18_700);
        assertThat(split.getBank()).isEqualTo(Bank.KAKAO);
        assertThat(split.getAccountNumber()).isEqualTo(ACCOUNT);
    }

    @DisplayName("ensureCancelable - 받는 사람이고 아무도 보내기 전인 OPEN 정산이면 통과한다")
    @Test
    void ensureCancelable() {

        /*
         * settings
         */
        FareSplit split = openSplit();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.ensureCancelable(PAYER_ID));

        /*
         * verify
         */
        assertThat(thrown).isNull();
    }

    // ───────────────────────── remindTransfers ─────────────────────────

    @DisplayName("remindTransfers - 다음 차례(4시간마다)가 되면 확인되지 않은 송금의 차례를 넘기고 아직 안 보낸 사람에게만 보낸다")
    @Test
    void remindTransfers() {

        /*
         * settings
         */
        // 2·3번째는 방해 금지 시간에 넘겼다
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, null, null, 3, NOW, null),
                FareSplitTransfer.load(LATE_RIDER_ID, SENT_AT, null, 3, NOW, null),
                FareSplitTransfer.load(CASH_RIDER_ID, null, SENT_AT, 3, NOW, null));

        /*
         * test
         */
        FareSplitNotice notice = split.remindTransfers(FOURTH_DUE);

        /*
         * verify
         */
        assertThat(notice.shouldSend()).isTrue();
        assertThat(notice.recipientIds()).containsExactly(RIDER_ID);
        FareSplitTransfer notSent = split.findTransfer(RIDER_ID).orElseThrow();
        assertThat(notSent.getRequestCount()).isEqualTo(4);
        assertThat(notSent.getLastRequestedAt()).isEqualTo(FOURTH_DUE);
        FareSplitTransfer sent = split.findTransfer(LATE_RIDER_ID).orElseThrow();
        assertThat(sent.getRequestCount()).as("보냈다고 누른 사람은 차례만 넘긴다").isEqualTo(4);
        assertThat(sent.getLastRequestedAt()).isEqualTo(NOW);
        FareSplitTransfer confirmed = split.findTransfer(CASH_RIDER_ID).orElseThrow();
        assertThat(confirmed.getRequestCount()).as("확인된 송금은 그대로").isEqualTo(3);
    }

    @DisplayName("remindTransfers - 다음 차례 전이면 아무것도 바꾸지 않는다")
    @Test
    void remindTransfers_notDue() {

        /*
         * settings
         */
        FareSplit beforeSecond = split(FareSplitStatus.OPEN, FareSplitTransfer.load(RIDER_ID, null, null, 1, NOW, null));
        FareSplit beforeFourth = split(FareSplitStatus.OPEN, FareSplitTransfer.load(RIDER_ID, null, null, 3, NOW, null));

        /*
         * test
         */
        FareSplitNotice second = beforeSecond.remindTransfers(SECOND_DUE.minusSeconds(1));
        FareSplitNotice fourth = beforeFourth.remindTransfers(FOURTH_DUE.minusSeconds(1));

        /*
         * verify
         */
        assertThat(second.handled()).isFalse();
        assertThat(beforeSecond.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(1);
        assertThat(fourth.handled()).as("3번째에서 4시간이 지나기 전").isFalse();
        assertThat(beforeFourth.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(3);
    }

    @DisplayName("remindTransfers - 마지막(6번째, 시작 20시간 뒤) 차례에 보내고, 그 뒤로는 만료 전이라도 더 보내지 않는다")
    @Test
    void remindTransfers_limit() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, FareSplitTransfer.load(RIDER_ID, null, null, 5, NOW, null));

        /*
         * test
         */
        FareSplitNotice last = split.remindTransfers(LAST_DUE);
        FareSplitNotice afterLimit = split.remindTransfers(NOW.plus(FareSplit.EXPIRES_AFTER).minusSeconds(1));

        /*
         * verify
         */
        assertThat(last.recipientIds()).containsExactly(RIDER_ID);
        assertThat(afterLimit.handled()).isFalse();
        FareSplitTransfer transfer = split.findTransfer(RIDER_ID).orElseThrow();
        assertThat(transfer.getRequestCount()).isEqualTo(TransferRequestSchedule.LIMIT);
        assertThat(transfer.getLastRequestedAt()).isEqualTo(LAST_DUE);
    }

    @DisplayName("remindTransfers - 방해 금지 시간이면 보내지 않고 차례만 넘긴다(나중에 몰아 보내지 않는다)")
    @Test
    void remindTransfers_quietHours() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, FareSplitTransfer.load(RIDER_ID, null, null, 1, NOW, null));

        /*
         * test
         */
        FareSplitNotice second = split.remindTransfers(SECOND_DUE);
        FareSplitNotice third = split.remindTransfers(THIRD_DUE);
        FareSplitNotice morning = split.remindTransfers(MORNING);

        /*
         * verify
         */
        assertThat(second.handled()).isTrue();
        assertThat(second.shouldSend()).isFalse();
        assertThat(third.handled()).isTrue();
        assertThat(third.shouldSend()).isFalse();
        assertThat(morning.handled()).as("방해 금지 시간이 끝나도 넘긴 차례는 다시 보내지 않는다").isFalse();
        FareSplitTransfer transfer = split.findTransfer(RIDER_ID).orElseThrow();
        assertThat(transfer.getRequestCount()).isEqualTo(3);
        assertThat(transfer.getLastRequestedAt()).isEqualTo(NOW);
    }

    @DisplayName("remindTransfers - 서버가 멈춰 여러 차례가 밀렸으면 지난 차례를 모두 넘기고 한 번만 보낸다")
    @Test
    void remindTransfers_catchUp() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, FareSplitTransfer.load(RIDER_ID, null, null, 1, NOW, null));
        Instant now = FOURTH_DUE.plus(Duration.ofHours(1));

        /*
         * test
         */
        FareSplitNotice notice = split.remindTransfers(now);

        /*
         * verify
         */
        assertThat(notice.recipientIds()).containsExactly(RIDER_ID);
        FareSplitTransfer transfer = split.findTransfer(RIDER_ID).orElseThrow();
        assertThat(transfer.getRequestCount()).isEqualTo(4);
        assertThat(transfer.getLastRequestedAt()).isEqualTo(now);
    }

    @DisplayName("remindTransfers - 차례가 지난 송금이 모두 보냈다고 누른 것이면 차례만 넘기고 보내지 않는다")
    @Test
    void remindTransfers_allSent() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, FareSplitTransfer.load(RIDER_ID, SENT_AT, null, 3, NOW, null));

        /*
         * test
         */
        FareSplitNotice notice = split.remindTransfers(FOURTH_DUE);

        /*
         * verify
         */
        assertThat(notice.handled()).isTrue();
        assertThat(notice.shouldSend()).isFalse();
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(4);
    }

    @DisplayName("remindTransfers - 닫힌 정산이나 받을 계좌가 지워진(받는 사람 탈퇴) 정산은 재요청하지 않는다")
    @Test
    void remindTransfers_closedOrNoAccount() {

        /*
         * settings
         */
        FareSplit expired = split(FareSplitStatus.EXPIRED, FareSplitTransfer.load(RIDER_ID, null, null, 1, NOW, null));
        FareSplit noAccount = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, 6_240, Bank.KAKAO, null, FareSplitStatus.OPEN, NOW, null,
                List.of(FareSplitTransfer.load(RIDER_ID, null, null, 1, NOW, null)));

        /*
         * test
         */
        FareSplitNotice closedNotice = expired.remindTransfers(SECOND_DUE);
        FareSplitNotice noAccountNotice = noAccount.remindTransfers(SECOND_DUE);

        /*
         * verify
         */
        assertThat(closedNotice.handled()).isFalse();
        assertThat(noAccountNotice.handled()).isFalse();
        assertThat(expired.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(1);
        assertThat(noAccount.findTransfer(RIDER_ID).orElseThrow().getRequestCount()).isEqualTo(1);
    }

    // ───────────────────────── requestConfirm ─────────────────────────

    @DisplayName("requestConfirm - 보냈다고 누르고 4시간이 지난(경계 포함) 송금에 입금 확인 요청 시각을 남기고 받는 사람에게 보낸다")
    @Test
    void requestConfirm() {

        /*
         * settings
         */
        Instant now = DAYTIME;
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, now.minus(Duration.ofHours(4)), null, 1, NOW, null),
                FareSplitTransfer.load(LATE_RIDER_ID, now.minus(Duration.ofHours(3)), null, 1, NOW, null));

        /*
         * test
         */
        FareSplitNotice notice = split.requestConfirm(now);

        /*
         * verify
         */
        assertThat(notice.recipientIds()).containsExactly(PAYER_ID);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getConfirmRequestedAt()).isEqualTo(now);
        assertThat(split.findTransfer(LATE_RIDER_ID).orElseThrow().getConfirmRequestedAt()).as("아직 4시간 전").isNull();
    }

    @DisplayName("requestConfirm - 차례가 된 송금이 여럿이어도 받는 사람에게 한 번만 보내고 모두 요청한 것으로 남긴다")
    @Test
    void requestConfirm_many() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, SENT_AT, null, 1, NOW, null),
                FareSplitTransfer.load(LATE_RIDER_ID, SENT_AT, null, 1, NOW, null));
        Instant now = DAYTIME;

        /*
         * test
         */
        FareSplitNotice notice = split.requestConfirm(now);

        /*
         * verify
         */
        assertThat(notice.recipientIds()).containsExactly(PAYER_ID);
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::getConfirmRequestedAt).containsOnly(now);
    }

    @DisplayName("requestConfirm - 4시간 전이거나 이미 요청했거나 SENT 가 아닌 송금뿐이면 아무것도 바꾸지 않는다")
    @Test
    void requestConfirm_notDue() {

        /*
         * settings
         */
        // 방해 금지 시간이 아닌 낮이어서 송금 조건만으로 가른다
        Instant now = DAYTIME;
        Instant requestedAt = MORNING;
        FareSplit split = split(FareSplitStatus.OPEN,
                FareSplitTransfer.load(RIDER_ID, now.minus(Duration.ofHours(4)).plusSeconds(1), null, 1, NOW, null),
                FareSplitTransfer.load(LATE_RIDER_ID, SENT_AT, null, 1, NOW, requestedAt),
                FareSplitTransfer.load(IDLE_RIDER_ID, null, null, 1, NOW, null),
                FareSplitTransfer.load(CASH_RIDER_ID, null, SENT_AT, 1, NOW, null));

        /*
         * test
         */
        FareSplitNotice notice = split.requestConfirm(now);

        /*
         * verify
         */
        assertThat(notice.handled()).isFalse();
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getConfirmRequestedAt()).isNull();
        assertThat(split.findTransfer(LATE_RIDER_ID).orElseThrow().getConfirmRequestedAt()).isEqualTo(requestedAt);
        assertThat(split.findTransfer(IDLE_RIDER_ID).orElseThrow().getConfirmRequestedAt()).isNull();
        assertThat(split.findTransfer(CASH_RIDER_ID).orElseThrow().getConfirmRequestedAt()).isNull();
    }

    @DisplayName("requestConfirm - 방해 금지 시간에는 아무것도 바꾸지 않고 기다렸다가, 끝나면 보낸다")
    @Test
    void requestConfirm_quietHours() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, FareSplitTransfer.load(RIDER_ID, SENT_AT, null, 1, NOW, null));
        // 보냄(한국 시각 11-03 21:45) 4시간 뒤인 11-04 01:45 와 방해 금지 시간이 끝난 08:00
        Instant quiet = SENT_AT.plus(Duration.ofHours(4));
        Instant morning = MORNING;

        /*
         * test
         */
        FareSplitNotice waiting = split.requestConfirm(quiet);
        Instant requestedWhileQuiet = split.findTransfer(RIDER_ID).orElseThrow().getConfirmRequestedAt();
        FareSplitNotice sent = split.requestConfirm(morning);

        /*
         * verify
         */
        assertThat(waiting.handled()).isFalse();
        assertThat(requestedWhileQuiet).isNull();
        assertThat(sent.recipientIds()).containsExactly(PAYER_ID);
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getConfirmRequestedAt()).isEqualTo(morning);
    }

    @DisplayName("requestConfirm - 닫힌 정산이면 아무것도 바꾸지 않는다")
    @Test
    void requestConfirm_closed() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.EXPIRED, FareSplitTransfer.load(RIDER_ID, SENT_AT, null, 1, NOW, null));

        /*
         * test
         */
        FareSplitNotice notice = split.requestConfirm(SENT_AT.plus(Duration.ofDays(2)));

        /*
         * verify
         */
        assertThat(notice.handled()).isFalse();
        assertThat(split.findTransfer(RIDER_ID).orElseThrow().getConfirmRequestedAt()).isNull();
    }

    // ───────────────────────── expire ─────────────────────────

    @DisplayName("expire - 시작하고 1일이 지나면 EXPIRED 로 닫고 닫힌 시각을 남기며 계좌번호를 지운다")
    @Test
    void expire() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, FareSplitTransfer.load(RIDER_ID, null, null, 3, NOW, null));
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
        assertThat(split.tossDeeplink()).isEmpty();
    }

    @DisplayName("expire - 1일이 지나기 전이면 그대로 둔다")
    @Test
    void expire_notYet() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.OPEN, FareSplitTransfer.load(RIDER_ID, null, null, 3, NOW, null));

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
        FareSplit split = split(status, FareSplitTransfer.load(RIDER_ID, SENT_AT, LATER, 1, NOW, null));

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

    @DisplayName("forgetAccount - 계좌번호를 지우고 true. 정산은 OPEN 으로 남고 계좌·딥링크를 보여 주지 않는다")
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
        assertThat(split.tossDeeplink()).isEmpty();
    }

    @DisplayName("forgetAccount - 이미 지웠으면(닫힌 정산 포함) false")
    @Test
    void forgetAccount_alreadyCleared() {

        /*
         * settings
         */
        FareSplit split = openSplit();
        split.forgetAccount();
        FareSplit completed = split(FareSplitStatus.COMPLETED, FareSplitTransfer.load(RIDER_ID, SENT_AT, LATER, 1, NOW, null));

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

    private static FareSplit openSplit() {
        return FareSplit.open(threeRiders(), PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, NOW);
    }

    /** 택시비 18,700원, 1인당 6,240원인 정산. 닫힌 정산은 계좌가 지워져 있다 */
    private static FareSplit split(FareSplitStatus status, FareSplitTransfer... transfers) {
        boolean open = status == FareSplitStatus.OPEN;

        return FareSplit.load(PARTY_ID, PAYER_ID, 18_700, 6_240, Bank.KAKAO, open ? ACCOUNT : null, status, NOW,
                open ? null : LATER, List.of(transfers));
    }

    /** 시작할 때 첫 송금 요청만 받고 아직 안 보낸 송금 */
    private static FareSplitTransfer notSent(Long memberId) {
        return FareSplitTransfer.load(memberId, null, null, 1, NOW, null);
    }

    /** 셋 다 도착해 탄 방. 합류 순 payer → rider → late rider */
    private static RideRoster threeRiders() {
        return finished(List.of(PAYER_ID, RIDER_ID, LATE_RIDER_ID), List.of(PAYER_ID, RIDER_ID, LATE_RIDER_ID));
    }

    /** 합류 순 payer(도착) → 노쇼 → rider(도착). 노쇼는 탑승자가 아니다(matching 이 정한다) */
    private static RideRoster withNoShow() {
        return finished(List.of(PAYER_ID, NO_SHOW_ID, RIDER_ID), List.of(PAYER_ID, RIDER_ID));
    }

    /** 합류 순 payer(도착) → 도착하지 않아 노쇼였다가 무효로 돌아간 멤버 → rider(도착). 무효 노쇼도 도착하지 않았으면 탑승자가 아니다 */
    private static RideRoster withVoidedNoShow() {
        return finished(List.of(PAYER_ID, VOIDED_NO_SHOW_ID, RIDER_ID), List.of(PAYER_ID, RIDER_ID));
    }

    /** 동승이 끝난 방. 멤버·탑승자 모두 합류 순 */
    private static RideRoster finished(List<Long> memberIds, List<Long> riderIds) {
        return new RideRoster(PARTY_ID, true, memberIds, riderIds);
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
