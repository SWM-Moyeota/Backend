package team.codingforest.moyeota.matching.faresplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.matching.faresplit.domain.Bank;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplit;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitRole;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitStatus;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitTransfer;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplits;
import team.codingforest.moyeota.matching.faresplit.domain.TossDeeplink;
import team.codingforest.moyeota.matching.faresplit.domain.TransferStatus;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;
import team.codingforest.moyeota.user.api.MaskedRealNames;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FareSplitServiceUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long PAYER_ID = 1L;
    private static final Long SENDER_ID = 2L;
    private static final Long OTHER_SENDER_ID = 3L;
    private static final Long OUTSIDER_ID = 99L;
    private static final UUID PAYER_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SENDER_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_SENDER_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant CREATED_AT = MEETING_AT.plus(Duration.ofMinutes(40));
    private static final Instant SENT_AT = CREATED_AT.plus(Duration.ofMinutes(5));
    private static final String RAW_ACCOUNT = "3333-01-2345678";
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");

    @Mock
    private Parties parties;
    @Mock
    private FareSplits fareSplits;
    @Mock
    private UserAccess userAccess;
    @Mock
    private MaskedRealNames maskedRealNames;
    @InjectMocks
    private FareSplitService service;

    // ───────────────────────── create ─────────────────────────

    @DisplayName("create - 방을 잠근 뒤 정산이 없으면 저장하고 받는 사람 화면(가린 실명 포함)을 돌려준다")
    @Test
    void create() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedParty())).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(false).when(fareSplits).existsByPartyId(eq(PARTY_ID));
        doAnswer(invocation -> invocation.getArgument(0)).when(fareSplits).create(any(FareSplit.class));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        doReturn(Map.of(SENDER_ID, "김*수", OTHER_SENDER_ID, "이*")).when(maskedRealNames).find(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.create(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, RAW_ACCOUNT);

        /*
         * verify
         */
        InOrder order = inOrder(parties, fareSplits);
        order.verify(parties).findByIdForUpdate(eq(PARTY_ID));
        order.verify(fareSplits).existsByPartyId(eq(PARTY_ID));
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        order.verify(fareSplits).create(captor.capture());

        FareSplit saved = captor.getValue();
        assertThat(saved.getPayerId()).isEqualTo(PAYER_ID);
        assertThat(saved.getTotalFare()).isEqualTo(18_700);
        assertThat(saved.getAmountPerPerson()).isEqualTo(6_240);
        assertThat(saved.getBank()).isEqualTo(Bank.KAKAO);
        assertThat(saved.getAccountNumber()).as("하이픈을 뗀 숫자로 저장").isEqualTo(ACCOUNT);
        assertThat(saved.senderIds()).containsExactly(SENDER_ID, OTHER_SENDER_ID);

        assertThat(detail.partyId()).isEqualTo(PARTY_ID);
        assertThat(detail.status()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(detail.myRole()).isEqualTo(FareSplitRole.PAYER);
        assertThat(detail.myAmount()).isEqualTo(6_220);
        assertThat(detail.payer().publicId()).isEqualTo(PAYER_PUBLIC_ID);
        assertThat(detail.payer().nickname()).isEqualTo("받는사람");
        assertThat(detail.bank().code()).isEqualTo(Bank.KAKAO);
        assertThat(detail.bank().name()).isEqualTo("카카오뱅크");
        assertThat(detail.accountNumber()).isEqualTo("3333012345678");
        assertThat(detail.tossDeeplink()).as("받는 사람에게는 딥링크를 주지 않는다").isNull();
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId)
                .containsExactly(SENDER_PUBLIC_ID, OTHER_SENDER_PUBLIC_ID);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::maskedRealName)
                .containsExactly("김*수", "이*");
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::status)
                .containsOnly(TransferStatus.NOT_SENT);
    }

    @DisplayName("create - 계좌번호가 잘못되면 INVALID_ACCOUNT_NUMBER 이고 방을 잠그지 않는다")
    @Test
    void create_invalidAccount() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, "123-456"));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.INVALID_ACCOUNT_NUMBER);
        verifyNoInteractions(parties, fareSplits, userAccess, maskedRealNames);
    }

    @DisplayName("create - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void create_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, RAW_ACCOUNT));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(fareSplits);
    }

    @DisplayName("create - 탑승자가 아니면 도메인이 NOT_A_RIDER 로 막고 정산 여부를 묻거나 저장하지 않는다")
    @Test
    void create_notRider() {

        /*
         * settings
         */
        Party party = party(PartyStatus.FINISHED, List.of(
                arrived(PAYER_ID, 0), arrived(SENDER_ID, 1), PartyMember.load(OTHER_SENDER_ID, MEETING_AT.minusSeconds(40), null, CREATED_AT, null)));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, OTHER_SENDER_ID, 18_700, Bank.KAKAO, RAW_ACCOUNT));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_A_RIDER);
        verifyNoInteractions(fareSplits);
    }

    @DisplayName("create - 이미 정산이 있으면 FARE_SPLIT_ALREADY_EXISTS 이고 저장하지 않는다")
    @Test
    void create_alreadyExists() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedParty())).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(true).when(fareSplits).existsByPartyId(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, SENDER_ID, 18_700, Bank.KAKAO, RAW_ACCOUNT));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_ALREADY_EXISTS);
        verify(fareSplits, never()).create(any());
        verifyNoInteractions(userAccess, maskedRealNames);
    }

    @DisplayName("create - 저장 중 PK 가 겹치면(DataIntegrityViolationException) FARE_SPLIT_ALREADY_EXISTS")
    @Test
    void create_duplicateKey() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedParty())).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(false).when(fareSplits).existsByPartyId(eq(PARTY_ID));
        doThrow(new DataIntegrityViolationException("duplicate key")).when(fareSplits).create(any(FareSplit.class));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, RAW_ACCOUNT));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_ALREADY_EXISTS);
        verifyNoInteractions(userAccess, maskedRealNames);
    }

    // ───────────────────────── getDetail ─────────────────────────

    @DisplayName("getDetail - 받는 사람이 보면 보내는 사람의 가린 실명·보낸 시각을 싣고 딥링크는 없다")
    @Test
    void getDetail_payer() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyId(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        doReturn(Map.of(SENDER_ID, "김*수")).when(maskedRealNames).find(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.getDetail(PARTY_ID, PAYER_ID);

        /*
         * verify
         */
        assertThat(detail.myRole()).isEqualTo(FareSplitRole.PAYER);
        assertThat(detail.myAmount()).isEqualTo(6_220);
        assertThat(detail.accountNumber()).isEqualTo("3333012345678");
        assertThat(detail.tossDeeplink()).isNull();
        assertThat(detail.transfers()).hasSize(2);
        FareSplitDetailResponse.FareSplitTransferItem sent = detail.transfers().get(0);
        assertThat(sent.publicId()).isEqualTo(SENDER_PUBLIC_ID);
        assertThat(sent.nickname()).isEqualTo("보낸사람");
        assertThat(sent.status()).isEqualTo(TransferStatus.SENT);
        assertThat(sent.sentAt()).isEqualTo(SENT_AT);
        assertThat(sent.maskedRealName()).isEqualTo("김*수");
        FareSplitDetailResponse.FareSplitTransferItem notSent = detail.transfers().get(1);
        assertThat(notSent.status()).isEqualTo(TransferStatus.NOT_SENT);
        assertThat(notSent.maskedRealName()).as("프로필이 없으면(탈퇴 등) null").isNull();
    }

    @DisplayName("getDetail - 보내는 사람이 보면 가린 실명을 묻지 않고 계좌·내 몫·딥링크를 준다")
    @Test
    void getDetail_sender() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyId(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.getDetail(PARTY_ID, OTHER_SENDER_ID);

        /*
         * verify
         */
        assertThat(detail.myRole()).isEqualTo(FareSplitRole.SENDER);
        assertThat(detail.myAmount()).isEqualTo(6_240);
        assertThat(detail.payer().publicId()).isEqualTo(PAYER_PUBLIC_ID);
        assertThat(detail.accountNumber()).isEqualTo("3333012345678");
        assertThat(detail.tossDeeplink()).isEqualTo(TossDeeplink.of(6_240, Bank.KAKAO, ACCOUNT));
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::maskedRealName)
                .containsOnlyNulls();
        verifyNoInteractions(maskedRealNames);
    }

    @DisplayName("getDetail - 닫힌 정산은 계좌·딥링크가 null 이고 닫힌 시각을 준다")
    @Test
    void getDetail_closed() {

        /*
         * settings
         */
        Instant closedAt = CREATED_AT.plus(Duration.ofDays(1));
        FareSplit completed = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, 6_240, Bank.KAKAO, null, FareSplitStatus.COMPLETED, CREATED_AT, closedAt,
                List.of(FareSplitTransfer.load(SENDER_ID, SENT_AT, closedAt, 1, CREATED_AT, null),
                        FareSplitTransfer.load(OTHER_SENDER_ID, null, closedAt, 1, CREATED_AT, null)));
        doReturn(Optional.of(completed)).when(fareSplits).findByPartyId(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.getDetail(PARTY_ID, SENDER_ID);

        /*
         * verify
         */
        assertThat(detail.status()).isEqualTo(FareSplitStatus.COMPLETED);
        assertThat(detail.accountNumber()).isNull();
        assertThat(detail.tossDeeplink()).isNull();
        assertThat(detail.closedAt()).isEqualTo(closedAt);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::status)
                .containsOnly(TransferStatus.CONFIRMED);
    }

    @DisplayName("getDetail - 정산이 없으면 FARE_SPLIT_NOT_FOUND")
    @Test
    void getDetail_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(fareSplits).findByPartyId(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.getDetail(PARTY_ID, PAYER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_NOT_FOUND);
        verifyNoInteractions(userAccess, maskedRealNames);
    }

    @DisplayName("getDetail - 받는 사람도 보내는 사람도 아니면 NOT_FARE_SPLIT_PARTICIPANT 이고 사용자 정보를 묻지 않는다")
    @Test
    void getDetail_notParticipant() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyId(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.getDetail(PARTY_ID, OUTSIDER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        verifyNoInteractions(userAccess, maskedRealNames);
    }

    // ───────────────────────── fixtures ─────────────────────────

    private static Map<Long, MemberSummary> summaries() {
        return Map.of(
                PAYER_ID, new MemberSummary(PAYER_PUBLIC_ID, "받는사람", null),
                SENDER_ID, new MemberSummary(SENDER_PUBLIC_ID, "보낸사람", null),
                OTHER_SENDER_ID, new MemberSummary(OTHER_SENDER_PUBLIC_ID, "아직안보냄", null));
    }

    /** 첫 번째 보내는 사람은 SENT, 두 번째는 NOT_SENT */
    private static FareSplit openSplit() {
        return FareSplit.load(PARTY_ID, PAYER_ID, 18_700, 6_240, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(FareSplitTransfer.load(SENDER_ID, SENT_AT, null, 1, CREATED_AT, null),
                        FareSplitTransfer.load(OTHER_SENDER_ID, null, null, 1, CREATED_AT, null)));
    }

    private static Party finishedParty() {
        return party(PartyStatus.FINISHED, List.of(arrived(PAYER_ID, 0), arrived(SENDER_ID, 1), arrived(OTHER_SENDER_ID, 2)));
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
