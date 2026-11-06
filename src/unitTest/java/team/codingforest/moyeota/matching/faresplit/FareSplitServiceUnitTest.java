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
import team.codingforest.moyeota.matching.faresplit.domain.OpenTransfer;
import team.codingforest.moyeota.matching.faresplit.domain.TossDeeplink;
import team.codingforest.moyeota.matching.faresplit.domain.TransferStatus;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.matching.faresplit.dto.MyFareSplitListResponse;
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
import static org.mockito.ArgumentMatchers.anyLong;
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


    // ───────────────────────── markSent ─────────────────────────

    @DisplayName("markSent - 정산 행을 잠근 뒤 내 송금을 SENT 로 바꿔 저장한다")
    @Test
    void markSent() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.markSent(PARTY_ID, OTHER_SENDER_ID);

        /*
         * verify
         */
        InOrder order = inOrder(fareSplits);
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        order.verify(fareSplits).save(captor.capture());

        FareSplitTransfer transfer = captor.getValue().findTransfer(OTHER_SENDER_ID).orElseThrow();
        assertThat(transfer.status()).isEqualTo(TransferStatus.SENT);
        assertThat(transfer.getSentAt()).isNotNull();
        verifyNoInteractions(parties, userAccess, maskedRealNames);
    }

    @DisplayName("markSent - 이미 SENT 면 저장하지 않는다")
    @Test
    void markSent_alreadySent() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.markSent(PARTY_ID, SENDER_ID);

        /*
         * verify
         */
        verify(fareSplits, never()).save(any());
    }

    @DisplayName("markSent - 받는 사람이 누르면 NOT_FARE_SPLIT_SENDER 이고 저장하지 않는다")
    @Test
    void markSent_payer() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.markSent(PARTY_ID, PAYER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_FARE_SPLIT_SENDER);
        verify(fareSplits, never()).save(any());
    }

    @DisplayName("markSent - 정산이 없으면(취소됨 포함) FARE_SPLIT_NOT_FOUND")
    @Test
    void markSent_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.markSent(PARTY_ID, SENDER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_NOT_FOUND);
        verify(fareSplits, never()).save(any());
    }

    // ───────────────────────── confirmReceived ─────────────────────────

    @DisplayName("confirmReceived - 정산 행을 잠근 뒤 publicId 를 보내는 사람 번호로 바꿔 확인하고 저장한다. 남은 송금이 있으면 OPEN 이다")
    @Test
    void confirmReceived() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(senderSummaries()).when(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        service.confirmReceived(PARTY_ID, PAYER_ID, SENDER_PUBLIC_ID);

        /*
         * verify
         */
        InOrder order = inOrder(fareSplits, userAccess);
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        order.verify(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        order.verify(fareSplits).save(captor.capture());

        FareSplit saved = captor.getValue();
        assertThat(saved.findTransfer(SENDER_ID).orElseThrow().status()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(saved.findTransfer(OTHER_SENDER_ID).orElseThrow().status()).isEqualTo(TransferStatus.NOT_SENT);
        assertThat(saved.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(saved.getAccountNumber()).isEqualTo(ACCOUNT);
    }

    @DisplayName("confirmReceived - 마지막 송금을 확인하면 COMPLETED·닫힌 시각·계좌 삭제를 함께 저장한다")
    @Test
    void confirmReceived_completes() {

        /*
         * settings
         */
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, 6_240, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(FareSplitTransfer.load(SENDER_ID, SENT_AT, SENT_AT.plusSeconds(60), 1, CREATED_AT, null),
                        FareSplitTransfer.load(OTHER_SENDER_ID, null, null, 1, CREATED_AT, null)));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(senderSummaries()).when(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        service.confirmReceived(PARTY_ID, PAYER_ID, OTHER_SENDER_PUBLIC_ID);

        /*
         * verify
         */
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        verify(fareSplits).save(captor.capture());
        FareSplit saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(FareSplitStatus.COMPLETED);
        assertThat(saved.getClosedAt()).isNotNull();
        assertThat(saved.getAccountNumber()).isNull();
        assertThat(saved.findTransfer(OTHER_SENDER_ID).orElseThrow().getSentAt()).as("현금 등으로 바로 확인").isNull();
    }

    @DisplayName("confirmReceived - 이미 CONFIRMED 면 저장하지 않는다")
    @Test
    void confirmReceived_alreadyConfirmed() {

        /*
         * settings
         */
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, 6_240, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(FareSplitTransfer.load(SENDER_ID, SENT_AT, SENT_AT.plusSeconds(60), 1, CREATED_AT, null),
                        FareSplitTransfer.load(OTHER_SENDER_ID, null, null, 1, CREATED_AT, null)));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(senderSummaries()).when(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        service.confirmReceived(PARTY_ID, PAYER_ID, SENDER_PUBLIC_ID);

        /*
         * verify
         */
        verify(fareSplits, never()).save(any());
    }

    @DisplayName("confirmReceived - publicId 가 이 정산의 보내는 사람이 아니면(받는 사람 자신·모르는 사람) FARE_SPLIT_TRANSFER_NOT_FOUND")
    @Test
    void confirmReceived_unknownPublicId() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(senderSummaries()).when(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        Throwable payerSelf = catchThrowable(() -> service.confirmReceived(PARTY_ID, PAYER_ID, PAYER_PUBLIC_ID));
        Throwable unknown = catchThrowable(() -> service.confirmReceived(PARTY_ID, PAYER_ID, UUID.fromString("00000000-0000-0000-0000-000000000099")));

        /*
         * verify
         */
        assertBusinessException(payerSelf, MatchingErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
        assertBusinessException(unknown, MatchingErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
        verify(fareSplits, never()).save(any());
    }

    @DisplayName("confirmReceived - 받는 사람이 아니면 NOT_FARE_SPLIT_PAYER 이고 사용자 정보를 묻거나 저장하지 않는다")
    @Test
    void confirmReceived_notPayer() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.confirmReceived(PARTY_ID, SENDER_ID, OTHER_SENDER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_FARE_SPLIT_PAYER);
        verifyNoInteractions(userAccess);
        verify(fareSplits, never()).save(any());
    }

    @DisplayName("confirmReceived - 정산이 없으면 FARE_SPLIT_NOT_FOUND")
    @Test
    void confirmReceived_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.confirmReceived(PARTY_ID, PAYER_ID, SENDER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_NOT_FOUND);
        verifyNoInteractions(userAccess);
    }

    // ───────────────────────── markNotReceived ─────────────────────────

    @DisplayName("markNotReceived - 정산 행을 잠근 뒤 SENT 송금을 NOT_SENT 로 되돌려 저장한다")
    @Test
    void markNotReceived() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(senderSummaries()).when(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        service.markNotReceived(PARTY_ID, PAYER_ID, SENDER_PUBLIC_ID);

        /*
         * verify
         */
        InOrder order = inOrder(fareSplits);
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        order.verify(fareSplits).save(captor.capture());

        FareSplitTransfer transfer = captor.getValue().findTransfer(SENDER_ID).orElseThrow();
        assertThat(transfer.status()).isEqualTo(TransferStatus.NOT_SENT);
        assertThat(transfer.getSentAt()).isNull();
    }

    @DisplayName("markNotReceived - SENT 가 아닌 송금이면 TRANSFER_NOT_SENT 이고 저장하지 않는다")
    @Test
    void markNotReceived_notSent() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(senderSummaries()).when(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.markNotReceived(PARTY_ID, PAYER_ID, OTHER_SENDER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.TRANSFER_NOT_SENT);
        verify(fareSplits, never()).save(any());
    }

    @DisplayName("markNotReceived - 받는 사람이 아니면 NOT_FARE_SPLIT_PAYER 이고 사용자 정보를 묻지 않는다")
    @Test
    void markNotReceived_notPayer() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.markNotReceived(PARTY_ID, OTHER_SENDER_ID, SENDER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_FARE_SPLIT_PAYER);
        verifyNoInteractions(userAccess);
        verify(fareSplits, never()).save(any());
    }

    // ───────────────────────── edit ─────────────────────────

    @DisplayName("edit - 정산 행을 잠근 뒤 택시비·계좌를 고쳐 저장하고 받는 사람 화면을 돌려준다")
    @Test
    void edit() {

        /*
         * settings
         */
        doReturn(Optional.of(notStartedSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        doReturn(Map.of(SENDER_ID, "김*수")).when(maskedRealNames).find(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.edit(PARTY_ID, PAYER_ID, 20_000, Bank.SHINHAN, "110-123-456789");

        /*
         * verify
         */
        InOrder order = inOrder(fareSplits);
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        order.verify(fareSplits).save(captor.capture());

        FareSplit saved = captor.getValue();
        assertThat(saved.getTotalFare()).isEqualTo(20_000);
        assertThat(saved.getAmountPerPerson()).isEqualTo(6_670);
        assertThat(saved.getBank()).isEqualTo(Bank.SHINHAN);
        assertThat(saved.getAccountNumber()).isEqualTo(new AccountNumber("110123456789"));

        assertThat(detail.myRole()).isEqualTo(FareSplitRole.PAYER);
        assertThat(detail.totalFare()).isEqualTo(20_000);
        assertThat(detail.amountPerPerson()).isEqualTo(6_670);
        assertThat(detail.myAmount()).isEqualTo(6_660);
        assertThat(detail.bank().code()).isEqualTo(Bank.SHINHAN);
        assertThat(detail.accountNumber()).isEqualTo("110123456789");
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::maskedRealName)
                .containsExactly("김*수", null);
    }

    @DisplayName("edit - 계좌번호가 잘못되면 INVALID_ACCOUNT_NUMBER 이고 정산을 잠그지 않는다")
    @Test
    void edit_invalidAccount() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID, 20_000, Bank.SHINHAN, "12345"));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.INVALID_ACCOUNT_NUMBER);
        verifyNoInteractions(fareSplits, userAccess, maskedRealNames);
    }

    @DisplayName("edit - 누군가 보냈다고 눌렀으면 FARE_SPLIT_ALREADY_STARTED 이고 저장하지 않는다")
    @Test
    void edit_alreadyStarted() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID, 20_000, Bank.SHINHAN, RAW_ACCOUNT));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_ALREADY_STARTED);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(userAccess, maskedRealNames);
    }

    @DisplayName("edit - 정산이 없으면 FARE_SPLIT_NOT_FOUND")
    @Test
    void edit_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID, 20_000, Bank.SHINHAN, RAW_ACCOUNT));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_NOT_FOUND);
        verify(fareSplits, never()).save(any());
    }

    // ───────────────────────── cancel ─────────────────────────

    @DisplayName("cancel - 정산 행을 잠근 뒤 아무도 보내기 전이면 정산을 지운다")
    @Test
    void cancel() {

        /*
         * settings
         */
        doReturn(Optional.of(notStartedSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.cancel(PARTY_ID, PAYER_ID);

        /*
         * verify
         */
        InOrder order = inOrder(fareSplits);
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        order.verify(fareSplits).delete(eq(PARTY_ID));
        verify(fareSplits, never()).save(any());
    }

    @DisplayName("cancel - 누군가 보냈다고 눌렀으면 FARE_SPLIT_ALREADY_STARTED 이고 지우지 않는다")
    @Test
    void cancel_alreadyStarted() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.cancel(PARTY_ID, PAYER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_ALREADY_STARTED);
        verify(fareSplits, never()).delete(anyLong());
    }

    @DisplayName("cancel - 보내는 사람이면 NOT_FARE_SPLIT_PAYER 이고 지우지 않는다")
    @Test
    void cancel_notPayer() {

        /*
         * settings
         */
        doReturn(Optional.of(notStartedSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.cancel(PARTY_ID, SENDER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_FARE_SPLIT_PAYER);
        verify(fareSplits, never()).delete(anyLong());
    }

    @DisplayName("cancel - 정산이 없으면(이미 취소함) FARE_SPLIT_NOT_FOUND")
    @Test
    void cancel_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.cancel(PARTY_ID, PAYER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.FARE_SPLIT_NOT_FOUND);
        verify(fareSplits, never()).delete(anyLong());
    }

    // ───────────────────────── findMine ─────────────────────────

    @DisplayName("findMine - 내 송금이 요청한 상태(NOT_SENT)인 정산만 받아 온 순서(최신순) 그대로, 받는 사람 닉네임과 함께 준다")
    @Test
    void findMine() {

        /*
         * settings
         */
        Long otherPartyId = 200L;
        Long otherPayerId = 5L;
        UUID otherPayerPublicId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        doReturn(List.of(
                new OpenTransfer(otherPartyId, "판교역", "강남역", otherPayerId, 9_350, null, null, CREATED_AT.plusSeconds(600)),
                new OpenTransfer(300L, "서울역", "홍대입구역", PAYER_ID, 4_000, SENT_AT, null, CREATED_AT.plusSeconds(300)),
                new OpenTransfer(PARTY_ID, "강남역", "판교역", PAYER_ID, 6_240, null, null, CREATED_AT)))
                .when(fareSplits).findOpenTransfers(eq(SENDER_ID));
        doReturn(Map.of(otherPayerId, new MemberSummary(otherPayerPublicId, "다른받는사람", null),
                        PAYER_ID, new MemberSummary(PAYER_PUBLIC_ID, "받는사람", null)))
                .when(userAccess).findMemberSummaries(eq(List.of(otherPayerId, PAYER_ID)));

        /*
         * test
         */
        MyFareSplitListResponse response = service.findMine(SENDER_ID, TransferStatus.NOT_SENT);

        /*
         * verify
         */
        assertThat(response.fareSplits()).hasSize(2);
        MyFareSplitListResponse.MyFareSplitItem first = response.fareSplits().get(0);
        assertThat(first.partyId()).isEqualTo(otherPartyId);
        assertThat(first.departure()).isEqualTo("판교역");
        assertThat(first.destination()).isEqualTo("강남역");
        assertThat(first.payer().publicId()).isEqualTo(otherPayerPublicId);
        assertThat(first.payer().nickname()).isEqualTo("다른받는사람");
        assertThat(first.myAmount()).isEqualTo(9_350);
        assertThat(first.status()).isEqualTo(TransferStatus.NOT_SENT);
        assertThat(first.createdAt()).isEqualTo(CREATED_AT.plusSeconds(600));
        assertThat(response.fareSplits().get(1).partyId()).isEqualTo(PARTY_ID);
        assertThat(response.fareSplits().get(1).payer().nickname()).isEqualTo("받는사람");
    }

    @DisplayName("findMine - status=SENT 면 보냈다고 누른 정산만 준다")
    @Test
    void findMine_sent() {

        /*
         * settings
         */
        doReturn(List.of(
                new OpenTransfer(300L, "서울역", "홍대입구역", PAYER_ID, 4_000, SENT_AT, null, CREATED_AT.plusSeconds(300)),
                new OpenTransfer(PARTY_ID, "강남역", "판교역", PAYER_ID, 6_240, null, null, CREATED_AT)))
                .when(fareSplits).findOpenTransfers(eq(SENDER_ID));
        doReturn(Map.of(PAYER_ID, new MemberSummary(PAYER_PUBLIC_ID, "받는사람", null)))
                .when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID)));

        /*
         * test
         */
        MyFareSplitListResponse response = service.findMine(SENDER_ID, TransferStatus.SENT);

        /*
         * verify
         */
        assertThat(response.fareSplits()).extracting(MyFareSplitListResponse.MyFareSplitItem::partyId).containsExactly(300L);
        assertThat(response.fareSplits().get(0).status()).isEqualTo(TransferStatus.SENT);
    }

    @DisplayName("findMine - 해당하는 정산이 없으면 빈 목록이고 사용자 정보를 묻지 않는다")
    @Test
    void findMine_empty() {

        /*
         * settings
         */
        doReturn(List.of(new OpenTransfer(300L, "서울역", "홍대입구역", PAYER_ID, 4_000, SENT_AT, null, CREATED_AT)))
                .when(fareSplits).findOpenTransfers(eq(SENDER_ID));

        /*
         * test
         */
        MyFareSplitListResponse response = service.findMine(SENDER_ID, TransferStatus.NOT_SENT);

        /*
         * verify
         */
        assertThat(response.fareSplits()).isEmpty();
        verifyNoInteractions(userAccess);
    }

    // ───────────────────────── fixtures ─────────────────────────

    private static Map<Long, MemberSummary> summaries() {
        return Map.of(
                PAYER_ID, new MemberSummary(PAYER_PUBLIC_ID, "받는사람", null),
                SENDER_ID, new MemberSummary(SENDER_PUBLIC_ID, "보낸사람", null),
                OTHER_SENDER_ID, new MemberSummary(OTHER_SENDER_PUBLIC_ID, "아직안보냄", null));
    }

    private static Map<Long, MemberSummary> senderSummaries() {
        return Map.of(
                SENDER_ID, new MemberSummary(SENDER_PUBLIC_ID, "보낸사람", null),
                OTHER_SENDER_ID, new MemberSummary(OTHER_SENDER_PUBLIC_ID, "아직안보냄", null));
    }

    /** 아무도 보내기 전 */
    private static FareSplit notStartedSplit() {
        return FareSplit.load(PARTY_ID, PAYER_ID, 18_700, 6_240, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(FareSplitTransfer.load(SENDER_ID, null, null, 1, CREATED_AT, null),
                        FareSplitTransfer.load(OTHER_SENDER_ID, null, null, 1, CREATED_AT, null)));
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
