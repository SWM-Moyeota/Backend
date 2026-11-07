package team.codingforest.moyeota.matching.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  정산 시작 규칙(탑승자·payer 자격, 택시비 범위), 1인당 금액(10원 단위 올림)과 payer 몫, 역할·계좌·딥링크 노출 규칙,
 *  송금 처리(보냄·받음·못 받음)와 완료, 수정·취소 조건
 */
class FareSplitUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long PAYER_ID = 1L;
    private static final Long RIDER_ID = 2L;
    private static final Long LATE_RIDER_ID = 3L;
    private static final Long NO_SHOW_ID = 4L;
    private static final Long VOIDED_NO_SHOW_ID = 5L;
    private static final Long OUTSIDER_ID = 99L;
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant NOW = MEETING_AT.plus(Duration.ofMinutes(40));
    private static final Instant SENT_AT = NOW.plus(Duration.ofMinutes(5));
    private static final Instant LATER = NOW.plus(Duration.ofHours(2));
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");
    private static final AccountNumber NEW_ACCOUNT = new AccountNumber("110123456789");

    // ───────────────────────── open ─────────────────────────

    @DisplayName("open - payer 를 뺀 탑승자마다 NOT_SENT 송금을 합류 순으로 만들고 1인당 금액은 탑승자 수로 나눠 10원 단위로 올린다")
    @Test
    void open() {

        /*
         * settings
         */
        Party party = threeRiders();

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
            assertThat(t.getRequestCount()).isZero();
            assertThat(t.getLastRequestedAt()).isNull();
            assertThat(t.getConfirmRequestedAt()).isNull();
        });
    }

    @DisplayName("open - 노쇼 멤버는 탑승자가 아니라 송금 대상에서 빠지고 N 에도 세지 않는다")
    @Test
    void open_excludesNoShow() {

        /*
         * settings
         */
        Party party = withNoShow();

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
        Party party = withVoidedNoShow();

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
        Party party = threeRiders();

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
        Party party = threeRiders();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, OUTSIDER_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_PARTY_MEMBER);
    }

    @DisplayName("open - 동승이 끝나지 않은(FINISHED 가 아닌) 방이면 PARTY_NOT_FINISHED")
    @ParameterizedTest
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED", "CANCELED"})
    void open_notFinished(PartyStatus status) {

        /*
         * settings
         */
        Party party = party(status, List.of(
                arrived(PAYER_ID, 0), arrived(RIDER_ID, 1)));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FINISHED);
    }

    @DisplayName("open - 노쇼로 찍힌 멤버는 payer 가 될 수 없어 NOT_A_RIDER")
    @Test
    void open_payerNoShow() {

        /*
         * settings
         */
        Party party = withNoShow();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, NO_SHOW_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_A_RIDER);
    }

    @DisplayName("open - 노쇼가 무효로 돌아갔어도 도착을 누르지 않은 멤버는 payer 가 될 수 없어 NOT_A_RIDER")
    @Test
    void open_payerVoidedNoShow() {

        /*
         * settings
         */
        Party party = withVoidedNoShow();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, VOIDED_NO_SHOW_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_A_RIDER);
    }

    @DisplayName("open - 도착 없이 끝난 방(약속을 정하지 않은 구버전 종료)은 아무도 탑승자가 아니라 NOT_A_RIDER")
    @Test
    void open_noArrivals() {

        /*
         * settings
         */
        Party party = party(PartyStatus.FINISHED, List.of(
                PartyMember.load(PAYER_ID, MEETING_AT.minusSeconds(60)),
                PartyMember.load(RIDER_ID, MEETING_AT.minusSeconds(30))));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_A_RIDER);
    }

    @DisplayName("open - 함께 탄 사람이 payer 혼자면 NOT_ENOUGH_RIDERS")
    @Test
    void open_aloneRider() {

        /*
         * settings
         */
        Party party = party(PartyStatus.FINISHED, List.of(
                arrived(PAYER_ID, 0),
                PartyMember.load(NO_SHOW_ID, MEETING_AT.minusSeconds(30), null, NOW, null)));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_ENOUGH_RIDERS);
    }

    @DisplayName("open - 택시비가 1,000~500,000원 밖이면 INVALID_FARE")
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 999, 500_001, Integer.MAX_VALUE})
    void open_invalidFare(int totalFare) {

        /*
         * settings
         */
        Party party = threeRiders();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> FareSplit.open(party, PAYER_ID, totalFare, Bank.KB, ACCOUNT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.INVALID_FARE);
    }

    @DisplayName("open - 택시비 경계값 1,000원과 500,000원은 받는다")
    @ParameterizedTest
    @ValueSource(ints = {FareSplit.MIN_TOTAL_FARE, FareSplit.MAX_TOTAL_FARE})
    void open_fareBoundary(int totalFare) {

        /*
         * settings
         */
        Party party = threeRiders();

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
        assertBusinessException(thrown, MatchingErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
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
        assertBusinessException(thrown, MatchingErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
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
        assertBusinessException(thrown, MatchingErrorCode.NOT_FARE_SPLIT_SENDER);
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
        assertBusinessException(thrown, MatchingErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    @DisplayName("markSent - 보내기 전에 닫힌(EXPIRED) 정산이면 FARE_SPLIT_CLOSED 이고 바꾸지 않는다")
    @Test
    void markSent_closed() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitStatus.EXPIRED, FareSplitTransfer.load(RIDER_ID, null, null, 4, NOW, null));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> split.markSent(RIDER_ID, LATER));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_CLOSED);
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
        assertBusinessException(bySender, MatchingErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(byOutsider, MatchingErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
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
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
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
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_CLOSED);
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
        assertBusinessException(notSent, MatchingErrorCode.TRANSFER_NOT_SENT);
        assertBusinessException(confirmed, MatchingErrorCode.TRANSFER_NOT_SENT);
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
        assertBusinessException(thrown, MatchingErrorCode.NOT_FARE_SPLIT_PAYER);
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
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_CLOSED);
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
        assertBusinessException(edited, MatchingErrorCode.FARE_SPLIT_ALREADY_STARTED);
        assertBusinessException(canceled, MatchingErrorCode.FARE_SPLIT_ALREADY_STARTED);
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
        FareSplit split = split(status, FareSplitTransfer.load(RIDER_ID, null, confirmedAt, 4, NOW, null));

        /*
         * test
         */
        Throwable edited = catchThrowable(() -> split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT));
        Throwable canceled = catchThrowable(() -> split.ensureCancelable(PAYER_ID));

        /*
         * verify
         */
        assertBusinessException(edited, MatchingErrorCode.FARE_SPLIT_CLOSED);
        assertBusinessException(canceled, MatchingErrorCode.FARE_SPLIT_CLOSED);
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
        assertBusinessException(editedBySender, MatchingErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(canceledBySender, MatchingErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(editedByOutsider, MatchingErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        assertBusinessException(canceledByOutsider, MatchingErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
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
        assertBusinessException(thrown, MatchingErrorCode.INVALID_FARE);
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

    /** 셋 다 도착해 탄 방. 합류 순 payer → rider → late rider */
    private static Party threeRiders() {
        return party(PartyStatus.FINISHED, List.of(arrived(PAYER_ID, 0), arrived(RIDER_ID, 1), arrived(LATE_RIDER_ID, 2)));
    }

    /** 합류 순 payer(도착) → 노쇼 → rider(도착) */
    private static Party withNoShow() {
        return party(PartyStatus.FINISHED, List.of(
                arrived(PAYER_ID, 0),
                PartyMember.load(NO_SHOW_ID, MEETING_AT.minusSeconds(50), null, NOW, null),
                arrived(RIDER_ID, 2)));
    }

    /** 합류 순 payer(도착) → 도착하지 않아 노쇼였다가 무효로 돌아간 멤버 → rider(도착) */
    private static Party withVoidedNoShow() {
        return party(PartyStatus.FINISHED, List.of(
                arrived(PAYER_ID, 0),
                PartyMember.load(VOIDED_NO_SHOW_ID, MEETING_AT.minusSeconds(50), null, NOW, NOW.plusSeconds(3600)),
                arrived(RIDER_ID, 2)));
    }

    private static PartyMember arrived(Long memberId, int joinOrder) {
        return PartyMember.load(memberId, MEETING_AT.minusSeconds(60 - joinOrder * 10L), MEETING_AT.minusSeconds(30), null, null);
    }

    private static Party party(PartyStatus status, List<PartyMember> members) {
        return Party.load(PARTY_ID, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(3), members,
                PAYER_ID, MEETING_AT.minus(Duration.ofHours(1)), status, 12000, 25, "encoded-path",
                7L, RoomType.REALTIME, MEETING_AT, null);
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
