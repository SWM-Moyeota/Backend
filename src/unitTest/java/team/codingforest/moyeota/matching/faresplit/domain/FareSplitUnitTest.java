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
 *  정산 시작 규칙(탑승자·payer 자격, 택시비 범위), 1인당 금액(10원 단위 올림)과 payer 몫, 역할·계좌·딥링크 노출 규칙
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
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");

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

    // ───────────────────────── fixtures ─────────────────────────

    private static FareSplit openSplit() {
        return FareSplit.open(threeRiders(), PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, NOW);
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
