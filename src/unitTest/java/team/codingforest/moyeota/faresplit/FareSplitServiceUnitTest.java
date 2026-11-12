package team.codingforest.moyeota.faresplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.FarePayment;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotifier;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitRole;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.FareSplitTransfer;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.faresplit.domain.OpenTransfer;
import team.codingforest.moyeota.faresplit.domain.TossDeeplink;
import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;
import team.codingforest.moyeota.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.faresplit.dto.FareSplitRequest;
import team.codingforest.moyeota.faresplit.dto.MyFareSplitListResponse;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartyPlaces;
import team.codingforest.moyeota.matching.api.dto.PartyRiders;
import team.codingforest.moyeota.user.api.MaskedRealNames;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
    private static final UUID OUTSIDER_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant CREATED_AT = MEETING_AT.plus(Duration.ofMinutes(40));
    private static final Instant SENT_AT = CREATED_AT.plus(Duration.ofMinutes(5));
    private static final String RAW_ACCOUNT = "3333-01-2345678";
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");
    private static final String REQUEST_TASK = "fare-split.transfer-request";
    private static final String CONFIRM_REQUEST_TASK = "fare-split.confirm-request";
    private static final String NOT_RECEIVED_TASK = "fare-split.not-received";
    private static final String COMPLETED_TASK = "fare-split.completed";

    @Mock
    private PartyAccess partyAccess;
    @Mock
    private FareSplits fareSplits;
    @Mock
    private UserAccess userAccess;
    @Mock
    private MaskedRealNames maskedRealNames;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private FareSplitNotifier notifier;
    @InjectMocks
    private FareSplitService service;

    // ───────────────────────── create ─────────────────────────

    @DisplayName("create - 방장이면 탑승자를 읽고 방장을 포함한 탑승자 전원의 publicId 를 userId 로 바꿔, 정산이 이미 있는지 미리 묻지 않고 바로 저장한다. 방장의 줄은 CONFIRMED·요청 횟수 0 으로 생기고, 응답은 받는 사람 화면(가린 실명 포함)이며 transfers 에 방장은 없다")
    @Test
    void create() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));
        // 탑승자 전원과 정산 참여자 전원이 같은 목록이라 publicId 변환과 응답에서 같은 인자로 두 번 불린다
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        doAnswer(invocation -> invocation.getArgument(0)).when(fareSplits).create(any(FareSplit.class));
        doReturn(Map.of(SENDER_ID, "김*수", OTHER_SENDER_ID, "이*")).when(maskedRealNames).find(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.create(PARTY_ID, PAYER_ID, evenRequest());

        /*
         * verify
         */
        InOrder order = inOrder(partyAccess, userAccess, fareSplits);
        order.verify(partyAccess).findRiders(eq(PARTY_ID));
        order.verify(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        order.verify(fareSplits).create(captor.capture());
        order.verify(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        verify(userAccess, times(2)).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        verifyNoMoreInteractions(fareSplits, userAccess);
        verify(maskedRealNames).find(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        FareSplit saved = captor.getValue();
        assertThat(saved.getPayerId()).isEqualTo(PAYER_ID);
        assertThat(saved.getTotalFare()).isEqualTo(18_700);
        assertThat(saved.getBank()).isEqualTo(Bank.KAKAO);
        assertThat(saved.getAccountNumber()).as("하이픈을 뗀 숫자로 저장").isEqualTo(ACCOUNT);
        assertThat(saved.participantIds()).as("방장의 줄도 저장한다").containsExactly(PAYER_ID, SENDER_ID, OTHER_SENDER_ID);
        assertThat(saved.senderIds()).containsExactly(SENDER_ID, OTHER_SENDER_ID);
        assertThat(saved.getTransfers()).extracting(FareSplitTransfer::getPayment).as("부담 금액의 합이 택시비")
                .containsExactly(6_220, 6_240, 6_240);
        FareSplitTransfer payerTransfer = saved.findTransfer(PAYER_ID).orElseThrow();
        assertThat(payerTransfer.status()).as("방장은 이미 낸 것이라 처음부터 CONFIRMED").isEqualTo(TransferStatus.CONFIRMED);
        assertThat(payerTransfer.getSentAt()).isEqualTo(saved.getCreatedAt());
        assertThat(payerTransfer.getConfirmedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(payerTransfer.getRequestCount()).as("방장에게는 송금 요청을 보내지 않는다").isZero();
        assertThat(saved.senderTransfers()).extracting(FareSplitTransfer::status).containsOnly(TransferStatus.NOT_SENT);
        assertThat(saved.senderTransfers()).extracting(FareSplitTransfer::getRequestCount).as("첫 송금 요청이 1번").containsOnly(1);

        assertThat(detail.partyId()).isEqualTo(PARTY_ID);
        assertThat(detail.status()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(detail.myRole()).isEqualTo(FareSplitRole.PAYER);
        assertThat(detail.myAmount()).as("방장 줄의 부담 금액").isEqualTo(6_220);
        assertThat(detail.payer().publicId()).isEqualTo(PAYER_PUBLIC_ID);
        assertThat(detail.payer().nickname()).isEqualTo("받는사람");
        assertThat(detail.bank().code()).isEqualTo(Bank.KAKAO);
        assertThat(detail.bank().name()).isEqualTo("카카오뱅크");
        assertThat(detail.accountNumber()).isEqualTo("3333012345678");
        assertThat(detail.tossDeeplink()).as("받는 사람에게는 딥링크를 주지 않는다").isNull();
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId).as("방장의 줄은 싣지 않는다")
                .containsExactly(SENDER_PUBLIC_ID, OTHER_SENDER_PUBLIC_ID);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::maskedRealName)
                .containsExactly("김*수", "이*");
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::amount)
                .containsOnly(6_240);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::status)
                .containsOnly(TransferStatus.NOT_SENT);
    }

    @DisplayName("create - 저장한 뒤 커밋되면 보내는 사람 전원에게 사람별 금액 목록으로 첫 송금 요청을 보낸다. 목록에 받는 사람(방장)은 없다")
    @Test
    void create_requestsTransfer() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));
        doAnswer(invocation -> invocation.getArgument(0)).when(fareSplits).create(any(FareSplit.class));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        service.create(PARTY_ID, PAYER_ID, evenRequest());

        /*
         * verify
         */
        InOrder order = inOrder(fareSplits, afterCommitExecutor);
        order.verify(fareSplits).create(any(FareSplit.class));
        order.verify(afterCommitExecutor).execute(eq(REQUEST_TASK), any());

        Runnable task = capturedTask(REQUEST_TASK);
        verifyNoInteractions(notifier);
        task.run();
        ArgumentCaptor<List<FarePayment>> payments = ArgumentCaptor.captor();
        verify(notifier).requestTransfer(eq(PARTY_ID), payments.capture());
        assertThat(payments.getValue()).containsExactly(new FarePayment(SENDER_ID, 6_240), new FarePayment(OTHER_SENDER_ID, 6_240));
        assertThat(payments.getValue()).extracting(FarePayment::memberId).doesNotContain(PAYER_ID);
        verifyNoMoreInteractions(notifier);
    }

    @DisplayName("create - 사람마다 다른 금액을 넣으면 받은 대로 방장을 포함한 각자의 줄에 담아 저장하고, 응답의 transfers[].amount·myAmount 와 송금 요청에도 각자 금액이 그대로 실린다")
    @Test
    void create_differentAmounts() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));
        doAnswer(invocation -> invocation.getArgument(0)).when(fareSplits).create(any(FareSplit.class));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        // 요청은 합류 순과 달리 other sender 를 먼저, 방장을 맨 뒤에 적었다
        FareSplitDetailResponse detail = service.create(PARTY_ID, PAYER_ID, request(18_700, Bank.KAKAO, RAW_ACCOUNT,
                payment(OTHER_SENDER_PUBLIC_ID, 5_000), payment(SENDER_PUBLIC_ID, 7_000), payment(PAYER_PUBLIC_ID, 6_700)));

        /*
         * verify
         */
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        verify(fareSplits).create(captor.capture());
        FareSplit saved = captor.getValue();
        assertThat(saved.findTransfer(SENDER_ID).orElseThrow().getPayment()).isEqualTo(7_000);
        assertThat(saved.findTransfer(OTHER_SENDER_ID).orElseThrow().getPayment()).isEqualTo(5_000);
        assertThat(saved.amountOf(PAYER_ID)).as("방장 줄의 부담 금액").isEqualTo(6_700);
        assertThat(saved.participantIds()).as("줄은 요청 순서가 아니라 합류 순").containsExactly(PAYER_ID, SENDER_ID, OTHER_SENDER_ID);

        assertThat(detail.totalFare()).isEqualTo(18_700);
        assertThat(detail.myAmount()).as("받는 사람 몫은 방장 줄의 부담 금액").isEqualTo(6_700);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId)
                .containsExactly(SENDER_PUBLIC_ID, OTHER_SENDER_PUBLIC_ID);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::amount)
                .containsExactly(7_000, 5_000);

        capturedTask(REQUEST_TASK).run();
        verify(notifier).requestTransfer(eq(PARTY_ID), eq(List.of(new FarePayment(SENDER_ID, 7_000), new FarePayment(OTHER_SENDER_ID, 5_000))));
    }

    @DisplayName("create - 탑승자가 아닌 publicId(방 밖 사람)는 서비스가, 같은 사람이 두 번 오면 도메인이 PAYMENT_TARGET_MISMATCH 로 막고 저장하지도 송금 요청을 보내지도 않는다")
    @Test
    void create_paymentTargetNotRiderOrDuplicated() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        Throwable outsider = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, request(18_700, Bank.KAKAO, RAW_ACCOUNT,
                payment(PAYER_PUBLIC_ID, 6_220), payment(SENDER_PUBLIC_ID, 6_240), payment(OUTSIDER_PUBLIC_ID, 6_240))));
        Throwable duplicated = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, request(18_700, Bank.KAKAO, RAW_ACCOUNT,
                payment(PAYER_PUBLIC_ID, 6_220), payment(SENDER_PUBLIC_ID, 6_240), payment(OTHER_SENDER_PUBLIC_ID, 6_240), payment(SENDER_PUBLIC_ID, 6_240))));
        Throwable payerTwice = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, request(18_700, Bank.KAKAO, RAW_ACCOUNT,
                payment(PAYER_PUBLIC_ID, 3_110), payment(SENDER_PUBLIC_ID, 6_240), payment(OTHER_SENDER_PUBLIC_ID, 6_240), payment(PAYER_PUBLIC_ID, 3_110))));

        /*
         * verify
         */
        assertBusinessException(outsider, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(duplicated, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(payerTwice, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        verifyNoInteractions(fareSplits, maskedRealNames, afterCommitExecutor, notifier);
    }

    @DisplayName("create - 방 멤버여도 도착하지 않아 타지 않은 사람의 publicId 가 오면 PAYMENT_TARGET_MISMATCH 이고 저장하지 않는다")
    @Test
    void create_paymentTargetNotRider() {

        /*
         * settings
         */
        // other sender 는 방 멤버지만 타지 않았다
        doReturn(Optional.of(new PartyRiders(PARTY_ID, PAYER_ID, true, List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID), List.of(PAYER_ID, SENDER_ID))))
                .when(partyAccess).findRiders(eq(PARTY_ID));
        doReturn(Map.of(PAYER_ID, new MemberSummary(PAYER_PUBLIC_ID, "받는사람", null), SENDER_ID, new MemberSummary(SENDER_PUBLIC_ID, "보낸사람", null)))
                .when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID)));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, evenRequest()));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        verifyNoInteractions(fareSplits, maskedRealNames, afterCommitExecutor, notifier);
    }

    @DisplayName("create - 탑승자가 한 명이라도 빠지면(보내는 사람이든 받는 사람인 방장이든) 도메인이 PAYMENT_TARGET_MISMATCH 로 막고 저장하지도 송금 요청을 보내지도 않는다")
    @Test
    void create_paymentMissing() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        // 둘 다 합은 택시비와 같다 - 빠진 사람이 있는지를 먼저 본다
        Throwable senderMissing = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID,
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, payment(PAYER_PUBLIC_ID, 9_350), payment(SENDER_PUBLIC_ID, 9_350))));
        Throwable payerMissing = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID,
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, payment(SENDER_PUBLIC_ID, 9_350), payment(OTHER_SENDER_PUBLIC_ID, 9_350))));

        /*
         * verify
         */
        assertBusinessException(senderMissing, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(payerMissing, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        verifyNoInteractions(fareSplits, maskedRealNames, afterCommitExecutor, notifier);
    }

    @DisplayName("create - 부담 금액의 합이 택시비와 다르면(1원 많든 적든) 도메인이 PAYMENTS_NOT_EQUAL_FARE 로 막고 저장하지도 송금 요청을 보내지도 않는다")
    @Test
    void create_paymentsNotEqualFare() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        // 6,220 + 6,240 + 6,241 = 18,701 로 택시비보다 1원 많다
        Throwable over = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, request(18_700, Bank.KAKAO, RAW_ACCOUNT,
                payment(PAYER_PUBLIC_ID, 6_220), payment(SENDER_PUBLIC_ID, 6_240), payment(OTHER_SENDER_PUBLIC_ID, 6_241))));
        // 6,220 + 6,240 + 6,239 = 18,699 로 택시비보다 1원 적다
        Throwable under = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, request(18_700, Bank.KAKAO, RAW_ACCOUNT,
                payment(PAYER_PUBLIC_ID, 6_220), payment(SENDER_PUBLIC_ID, 6_240), payment(OTHER_SENDER_PUBLIC_ID, 6_239))));

        /*
         * verify
         */
        assertBusinessException(over, FareSplitErrorCode.PAYMENTS_NOT_EQUAL_FARE);
        assertBusinessException(under, FareSplitErrorCode.PAYMENTS_NOT_EQUAL_FARE);
        verifyNoInteractions(fareSplits, maskedRealNames, afterCommitExecutor, notifier);
    }

    @DisplayName("create - 저장소가 줄을 userId 순으로 돌려줘도 transfers 는 정산을 시작할 때 읽은 방 합류 순이고 방장은 없다. 방은 한 번만 읽는다")
    @Test
    void create_joinOrder() {

        /*
         * settings
         */
        doReturn(Optional.of(crossedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));
        // publicId 변환은 탑승자 합류 순(other sender → payer → sender)으로, 응답은 저장소가 돌려준 userId 순으로 묻는다
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(OTHER_SENDER_ID, PAYER_ID, SENDER_ID)));
        doAnswer(invocation -> inUserIdOrder(invocation.getArgument(0))).when(fareSplits).create(any(FareSplit.class));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.create(PARTY_ID, PAYER_ID, evenRequest());

        /*
         * verify
         */
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId)
                .containsExactly(OTHER_SENDER_PUBLIC_ID, SENDER_PUBLIC_ID);
        verify(partyAccess, times(1)).findRiders(eq(PARTY_ID));
        verifyNoMoreInteractions(partyAccess);
    }

    @DisplayName("create - 계좌번호가 잘못되면 INVALID_ACCOUNT_NUMBER 이고 방을 읽지 않는다")
    @Test
    void create_invalidAccount() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID,
                request(18_700, Bank.KAKAO, "123-456", payment(PAYER_PUBLIC_ID, 6_220), payment(SENDER_PUBLIC_ID, 6_240), payment(OTHER_SENDER_PUBLIC_ID, 6_240))));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.INVALID_ACCOUNT_NUMBER);
        verifyNoInteractions(partyAccess, fareSplits, userAccess, maskedRealNames);
    }

    @DisplayName("create - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void create_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(partyAccess).findRiders(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, evenRequest()));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(fareSplits, userAccess);
    }

    @DisplayName("create - 방 창구가 알려 준 방장(hostId)을 명단에 옮겨 담아, 맨 먼저 합류하지 않았어도 방장이면 받는 사람으로 정산을 시작한다. 방장의 줄만 CONFIRMED 다")
    @Test
    void create_hostFromRiders() {

        /*
         * settings
         */
        List<Long> joinOrder = List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID);
        doReturn(Optional.of(new PartyRiders(PARTY_ID, OTHER_SENDER_ID, true, joinOrder, joinOrder))).when(partyAccess).findRiders(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        doAnswer(invocation -> invocation.getArgument(0)).when(fareSplits).create(any(FareSplit.class));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.create(PARTY_ID, OTHER_SENDER_ID, request(18_700, Bank.KAKAO, RAW_ACCOUNT,
                payment(PAYER_PUBLIC_ID, 6_240), payment(SENDER_PUBLIC_ID, 6_240), payment(OTHER_SENDER_PUBLIC_ID, 6_220)));

        /*
         * verify
         */
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        verify(fareSplits).create(captor.capture());
        FareSplit saved = captor.getValue();
        assertThat(saved.getPayerId()).isEqualTo(OTHER_SENDER_ID);
        assertThat(saved.participantIds()).as("줄은 합류 순, 방장 포함").containsExactly(PAYER_ID, SENDER_ID, OTHER_SENDER_ID);
        assertThat(saved.senderIds()).containsExactly(PAYER_ID, SENDER_ID);
        assertThat(saved.getTransfers()).extracting(FareSplitTransfer::status)
                .containsExactly(TransferStatus.NOT_SENT, TransferStatus.NOT_SENT, TransferStatus.CONFIRMED);
        assertThat(saved.getTransfers()).extracting(FareSplitTransfer::getRequestCount).containsExactly(1, 1, 0);
        assertThat(detail.myRole()).isEqualTo(FareSplitRole.PAYER);
        assertThat(detail.myAmount()).isEqualTo(6_220);
        assertThat(detail.payer().publicId()).isEqualTo(OTHER_SENDER_PUBLIC_ID);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId)
                .containsExactly(PAYER_PUBLIC_ID, SENDER_PUBLIC_ID);
    }

    @DisplayName("create - 방장이 아니면(함께 탄 멤버여도) 사용자를 조회하기 전에 NOT_PARTY_HOST 로 막고 저장하지도 송금 요청을 보내지도 않는다")
    @Test
    void create_notHost() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));

        /*
         * test
         */
        Throwable rider = catchThrowable(() -> service.create(PARTY_ID, SENDER_ID, evenRequest()));
        Throwable outsider = catchThrowable(() -> service.create(PARTY_ID, OUTSIDER_ID, evenRequest()));

        /*
         * verify
         */
        assertBusinessException(rider, FareSplitErrorCode.NOT_PARTY_HOST);
        assertBusinessException(outsider, FareSplitErrorCode.NOT_PARTY_HOST);
        verifyNoInteractions(userAccess, fareSplits, maskedRealNames, afterCommitExecutor, notifier);
    }

    @DisplayName("create - 동승이 끝나지 않은 방이면 PARTY_NOT_FINISHED 로 막고 저장하지 않는다")
    @Test
    void create_notFinished() {

        /*
         * settings
         */
        doReturn(Optional.of(new PartyRiders(PARTY_ID, PAYER_ID, false, List.of(PAYER_ID, SENDER_ID), List.of(PAYER_ID, SENDER_ID))))
                .when(partyAccess).findRiders(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID,
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, payment(PAYER_PUBLIC_ID, 9_350), payment(SENDER_PUBLIC_ID, 9_350))));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.PARTY_NOT_FINISHED);
        verifyNoInteractions(userAccess, fareSplits, afterCommitExecutor);
    }

    @DisplayName("create - 방장이 택시에 타지 않았으면 NOT_A_RIDER 로 막고 저장하지 않는다")
    @Test
    void create_notRider() {

        /*
         * settings
         */
        doReturn(Optional.of(new PartyRiders(PARTY_ID, PAYER_ID, true, List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID), List.of(SENDER_ID, OTHER_SENDER_ID))))
                .when(partyAccess).findRiders(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, evenRequest()));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.NOT_A_RIDER);
        verifyNoInteractions(userAccess, fareSplits, afterCommitExecutor);
    }

    @DisplayName("create - 같은 방에 이미 정산이 있어 저장 중 PK 가 겹치면(DuplicateKeyException) FARE_SPLIT_ALREADY_EXISTS 이고 송금 요청을 보내지 않는다")
    @Test
    void create_duplicateKey() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        doThrow(new DuplicateKeyException("duplicate key")).when(fareSplits).create(any(FareSplit.class));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, evenRequest()));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_ALREADY_EXISTS);
        verify(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        verifyNoMoreInteractions(userAccess);
        verifyNoInteractions(maskedRealNames, afterCommitExecutor);
    }

    @DisplayName("create - 중복 키가 아닌 무결성 위반(FK 등)은 FARE_SPLIT_ALREADY_EXISTS 로 감추지 않고 그대로 올리며 송금 요청을 보내지 않는다")
    @Test
    void create_otherIntegrityViolation() {

        /*
         * settings
         */
        DataIntegrityViolationException violation = new DataIntegrityViolationException("violates foreign key constraint");
        doReturn(Optional.of(finishedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        doThrow(violation).when(fareSplits).create(any(FareSplit.class));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.create(PARTY_ID, PAYER_ID, evenRequest()));

        /*
         * verify
         */
        assertThat(thrown).isSameAs(violation);
        verify(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        verifyNoMoreInteractions(userAccess);
        verifyNoInteractions(maskedRealNames, afterCommitExecutor);
    }

    // ───────────────────────── getDetail ─────────────────────────

    @DisplayName("getDetail - 받는 사람이 보면 보내는 사람의 가린 실명·보낸 시각을 싣고 딥링크는 없다. transfers 에 받는 사람 자신의 줄은 없다")
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
        assertThat(detail.myAmount()).as("받는 사람 줄의 부담 금액").isEqualTo(6_220);
        assertThat(detail.accountNumber()).isEqualTo("3333012345678");
        assertThat(detail.tossDeeplink()).isNull();
        assertThat(detail.transfers()).as("받는 사람의 줄은 싣지 않는다").hasSize(2);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId).doesNotContain(PAYER_PUBLIC_ID);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::amount).containsOnly(6_240);
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
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId).as("받는 사람의 줄은 싣지 않는다")
                .containsExactly(SENDER_PUBLIC_ID, OTHER_SENDER_PUBLIC_ID);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::maskedRealName)
                .containsOnlyNulls();
        verifyNoInteractions(maskedRealNames);
    }

    @DisplayName("getDetail - 사람마다 부담 금액이 다르면 보내는 사람은 자기 금액을 내 몫·딥링크로, 받는 사람은 자기 줄의 부담 금액을 내 몫으로 받는다")
    @Test
    void getDetail_differentAmounts() {

        /*
         * settings
         */
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(payerTransfer(6_700),
                        FareSplitTransfer.load(SENDER_ID, 7_000, null, null, 1),
                        FareSplitTransfer.load(OTHER_SENDER_ID, 5_000, null, null, 1)));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyId(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse sender = service.getDetail(PARTY_ID, SENDER_ID);
        FareSplitDetailResponse otherSender = service.getDetail(PARTY_ID, OTHER_SENDER_ID);
        FareSplitDetailResponse payer = service.getDetail(PARTY_ID, PAYER_ID);

        /*
         * verify
         */
        assertThat(sender.myAmount()).isEqualTo(7_000);
        assertThat(sender.tossDeeplink()).isEqualTo(TossDeeplink.of(7_000, Bank.KAKAO, ACCOUNT));
        assertThat(otherSender.myAmount()).isEqualTo(5_000);
        assertThat(otherSender.tossDeeplink()).isEqualTo(TossDeeplink.of(5_000, Bank.KAKAO, ACCOUNT));
        assertThat(payer.myAmount()).as("받는 사람 줄의 부담 금액").isEqualTo(6_700);
        assertThat(payer.tossDeeplink()).isNull();
        assertThat(sender.myTransferStatus()).as("보내는 사람은 자기 줄의 송금 상태").isEqualTo(TransferStatus.NOT_SENT);
        assertThat(payer.myTransferStatus()).as("받는 사람은 송금 상태가 없다").isNull();
        assertThat(payer.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::amount)
                .containsExactly(7_000, 5_000);
        assertThat(sender.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::amount)
                .containsExactly(7_000, 5_000);
    }

    @DisplayName("getDetail - 닫힌 정산은 계좌·딥링크가 null 이고 닫힌 시각을 준다")
    @Test
    void getDetail_closed() {

        /*
         * settings
         */
        Instant closedAt = CREATED_AT.plus(Duration.ofDays(1));
        FareSplit completed = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, null, FareSplitStatus.COMPLETED, CREATED_AT, closedAt,
                List.of(payerTransfer(6_220),
                        FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, closedAt, 1),
                        FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, null, closedAt, 1)));
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

    @DisplayName("getDetail - 저장소가 줄을 userId 순으로 돌려줘도 transfers 는 방 창구가 알려 준 합류 순이고 받는 사람은 없다. 방은 한 번만 읽는다")
    @Test
    void getDetail_joinOrder() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyId(eq(PARTY_ID));
        doReturn(Optional.of(crossedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        doReturn(Map.of(SENDER_ID, "김*수", OTHER_SENDER_ID, "이*")).when(maskedRealNames).find(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.getDetail(PARTY_ID, PAYER_ID);

        /*
         * verify
         */
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId)
                .containsExactly(OTHER_SENDER_PUBLIC_ID, SENDER_PUBLIC_ID);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::status)
                .containsExactly(TransferStatus.NOT_SENT, TransferStatus.SENT);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::maskedRealName)
                .containsExactly("이*", "김*수");
        verify(partyAccess, times(1)).findRiders(eq(PARTY_ID));
        verifyNoMoreInteractions(partyAccess);
    }

    @DisplayName("getDetail - 방을 읽지 못하면(empty) 오류 없이 transfers 를 userId 순으로 담는다")
    @Test
    void getDetail_partyGone() {

        /*
         * settings
         */
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(payerTransfer(6_220),
                        FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, null, null, 1),
                        FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, null, 1)));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyId(eq(PARTY_ID));
        doReturn(Optional.empty()).when(partyAccess).findRiders(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, OTHER_SENDER_ID, SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.getDetail(PARTY_ID, SENDER_ID);

        /*
         * verify
         */
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId)
                .containsExactly(SENDER_PUBLIC_ID, OTHER_SENDER_PUBLIC_ID);
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
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_NOT_FOUND);
        verifyNoInteractions(userAccess, maskedRealNames);
    }

    @DisplayName("getDetail - 받는 사람도 보내는 사람도 아니면 NOT_FARE_SPLIT_PARTICIPANT 이고 방·사용자 정보를 묻지 않는다")
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
        assertBusinessException(thrown, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        verifyNoInteractions(partyAccess, userAccess, maskedRealNames);
    }

    // ───────────────────────── markSent ─────────────────────────

    @DisplayName("markSent - 정산 행을 잠근 뒤 내 송금을 SENT 로 바꿔 저장하고, 커밋되면 받는 사람에게 입금 확인을 요청한다")
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
        InOrder order = inOrder(fareSplits, afterCommitExecutor);
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        order.verify(fareSplits).save(captor.capture());
        order.verify(afterCommitExecutor).execute(eq(CONFIRM_REQUEST_TASK), any());

        FareSplitTransfer transfer = captor.getValue().findTransfer(OTHER_SENDER_ID).orElseThrow();
        assertThat(transfer.status()).isEqualTo(TransferStatus.SENT);
        assertThat(transfer.getSentAt()).isNotNull();

        Runnable task = capturedTask(CONFIRM_REQUEST_TASK);
        verifyNoInteractions(notifier);
        task.run();
        verify(notifier).requestConfirm(eq(PARTY_ID), eq(PAYER_ID));
        verifyNoMoreInteractions(notifier);
        verifyNoInteractions(partyAccess, userAccess, maskedRealNames);
    }

    @DisplayName("markSent - 이미 SENT·CONFIRMED 면 저장하지도 입금 확인을 요청하지도 않는다")
    @Test
    void markSent_alreadySent() {

        /*
         * settings
         */
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(payerTransfer(6_220),
                        FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, null, 1),
                        FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, null, SENT_AT.plusSeconds(60), 1)));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.markSent(PARTY_ID, SENDER_ID);
        service.markSent(PARTY_ID, OTHER_SENDER_ID);

        /*
         * verify
         */
        assertThat(split.findTransfer(SENDER_ID).orElseThrow().getSentAt()).as("처음 누른 시각 그대로").isEqualTo(SENT_AT);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, notifier);
    }

    @DisplayName("markSent - 닫힌 정산이면 이미 SENT 였어도 FARE_SPLIT_CLOSED 이고 저장하지도 알리지도 않는다")
    @Test
    void markSent_closed() {

        /*
         * settings
         */
        Instant closedAt = CREATED_AT.plus(Duration.ofDays(1));
        FareSplit expired = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, null, FareSplitStatus.EXPIRED, CREATED_AT, closedAt,
                List.of(payerTransfer(6_220),
                        FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, null, 1),
                        FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, null, null, 6)));
        doReturn(Optional.of(expired)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable alreadySent = catchThrowable(() -> service.markSent(PARTY_ID, SENDER_ID));
        Throwable notSent = catchThrowable(() -> service.markSent(PARTY_ID, OTHER_SENDER_ID));

        /*
         * verify
         */
        assertBusinessException(alreadySent, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertBusinessException(notSent, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, notifier);
    }

    @DisplayName("markSent - 받는 사람이 누르면 NOT_FARE_SPLIT_SENDER 이고 저장하지도 알리지도 않는다")
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
        assertBusinessException(thrown, FareSplitErrorCode.NOT_FARE_SPLIT_SENDER);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor);
    }

    @DisplayName("markSent - 정산이 없으면 FARE_SPLIT_NOT_FOUND")
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
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_NOT_FOUND);
        verify(fareSplits, never()).save(any());
    }

    // ───────────────────────── confirmReceived ─────────────────────────

    @DisplayName("confirmReceived - 정산 행을 잠근 뒤 publicId 를 보내는 사람 번호로 바꿔 확인하고 저장한다. 남은 송금이 있으면 OPEN 이고 완료 알림은 없다")
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
        assertThat(saved.getStatus()).as("받는 사람의 줄이 CONFIRMED 여도 보내는 사람이 남아 있으면 열려 있다").isEqualTo(FareSplitStatus.OPEN);
        assertThat(saved.getAccountNumber()).isEqualTo(ACCOUNT);
        verifyNoInteractions(partyAccess, afterCommitExecutor);
    }

    @DisplayName("confirmReceived - 마지막 송금을 확인하면 COMPLETED·닫힌 시각·계좌 삭제를 함께 저장하고, 커밋되면 받는 사람과 보내는 사람 전원에게 완료 알림을 보낸다")
    @Test
    void confirmReceived_completes() {

        /*
         * settings
         */
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(payerTransfer(6_220),
                        FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, SENT_AT.plusSeconds(60), 1),
                        FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, null, null, 1)));
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

        Runnable task = capturedTask(COMPLETED_TASK);
        verifyNoInteractions(notifier);
        task.run();
        verify(notifier).notifyCompleted(eq(PARTY_ID), eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        verifyNoInteractions(partyAccess);
    }

    @DisplayName("confirmReceived - 이미 CONFIRMED 면 저장하지 않고 알림도 없다")
    @Test
    void confirmReceived_alreadyConfirmed() {

        /*
         * settings
         */
        FareSplit split = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(payerTransfer(6_220),
                        FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, SENT_AT.plusSeconds(60), 1),
                        FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, null, null, 1)));
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
        verifyNoInteractions(afterCommitExecutor);
    }

    @DisplayName("confirmReceived - publicId 가 이 정산에 없는 사람이면 FARE_SPLIT_TRANSFER_NOT_FOUND 이고 저장하지 않는다")
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
        Throwable thrown = catchThrowable(() -> service.confirmReceived(PARTY_ID, PAYER_ID, OUTSIDER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor);
    }

    @DisplayName("confirmReceived - 받는 사람(방장) 자신의 publicId 면 줄이 있어도 보내는 사람만으로 바꾸는 변환에서 FARE_SPLIT_TRANSFER_NOT_FOUND 이고 저장하지도 알리지도 않는다")
    @Test
    void confirmReceived_payerPublicId() {

        /*
         * settings
         */
        FareSplit split = openSplit();
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        // 변환은 받는 사람을 뺀 보내는 사람(senderIds)으로만 묻는다
        doReturn(senderSummaries()).when(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.confirmReceived(PARTY_ID, PAYER_ID, PAYER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
        verify(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));
        verifyNoMoreInteractions(userAccess);
        assertThat(split.findTransfer(PAYER_ID).orElseThrow().getConfirmedAt()).as("받는 사람의 줄은 그대로").isEqualTo(CREATED_AT);
        assertThat(split.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, notifier);
    }

    @DisplayName("confirmReceived - 닫힌 정산이면 이미 CONFIRMED 였어도 FARE_SPLIT_CLOSED 이고 저장하지도 알리지도 않는다")
    @Test
    void confirmReceived_closed() {

        /*
         * settings
         */
        Instant closedAt = CREATED_AT.plus(Duration.ofDays(1));
        FareSplit expired = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, null, FareSplitStatus.EXPIRED, CREATED_AT, closedAt,
                List.of(payerTransfer(6_220),
                        FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, SENT_AT.plusSeconds(60), 1),
                        FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, SENT_AT, null, 1)));
        doReturn(Optional.of(expired)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(senderSummaries()).when(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        Throwable alreadyConfirmed = catchThrowable(() -> service.confirmReceived(PARTY_ID, PAYER_ID, SENDER_PUBLIC_ID));
        Throwable sent = catchThrowable(() -> service.confirmReceived(PARTY_ID, PAYER_ID, OTHER_SENDER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(alreadyConfirmed, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertBusinessException(sent, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor);
    }

    @DisplayName("confirmReceived - 받는 사람이 아니면(보내는 사람은 NOT_FARE_SPLIT_PAYER, 정산에 없는 사람은 NOT_FARE_SPLIT_PARTICIPANT) 사용자 정보를 묻기 전에 막고 저장하지 않는다")
    @Test
    void confirmReceived_notPayer() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable sender = catchThrowable(() -> service.confirmReceived(PARTY_ID, SENDER_ID, OTHER_SENDER_PUBLIC_ID));
        Throwable outsider = catchThrowable(() -> service.confirmReceived(PARTY_ID, OUTSIDER_ID, SENDER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(sender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(outsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        verifyNoInteractions(userAccess, afterCommitExecutor);
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
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_NOT_FOUND);
        verifyNoInteractions(userAccess);
    }

    // ───────────────────────── markNotReceived ─────────────────────────

    @DisplayName("markNotReceived - 정산 행을 잠근 뒤 SENT 송금의 보낸 시각만 지워 NOT_SENT 로 되돌려 저장하고(요청 횟수는 그대로), 커밋되면 그 보내는 사람에게 못 받음 알림을 보낸다")
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
        InOrder order = inOrder(fareSplits, userAccess);
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        order.verify(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        order.verify(fareSplits).save(captor.capture());

        FareSplitTransfer transfer = captor.getValue().findTransfer(SENDER_ID).orElseThrow();
        assertThat(transfer.status()).isEqualTo(TransferStatus.NOT_SENT);
        assertThat(transfer.getSentAt()).isNull();
        assertThat(transfer.getRequestCount()).as("못 받았어요는 송금 요청 횟수에 넣지 않는다").isEqualTo(1);

        Runnable task = capturedTask(NOT_RECEIVED_TASK);
        verifyNoInteractions(notifier);
        task.run();
        verify(notifier).notifyNotReceived(eq(PARTY_ID), eq(SENDER_ID));
        verifyNoInteractions(partyAccess);
    }

    @DisplayName("markNotReceived - SENT 가 아닌 송금이면 TRANSFER_NOT_SENT 이고 저장하지도 알리지도 않는다")
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
        assertBusinessException(thrown, FareSplitErrorCode.TRANSFER_NOT_SENT);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor);
    }

    @DisplayName("markNotReceived - publicId 가 이 정산에 없는 사람이면 FARE_SPLIT_TRANSFER_NOT_FOUND 이고 저장하지도 알리지도 않는다")
    @Test
    void markNotReceived_unknownPublicId() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(senderSummaries()).when(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.markNotReceived(PARTY_ID, PAYER_ID, OUTSIDER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor);
    }

    @DisplayName("markNotReceived - 받는 사람(방장) 자신의 publicId 면 줄이 있어도 보내는 사람만으로 바꾸는 변환에서 FARE_SPLIT_TRANSFER_NOT_FOUND 이고 저장하지도 알리지도 않는다")
    @Test
    void markNotReceived_payerPublicId() {

        /*
         * settings
         */
        FareSplit split = openSplit();
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        // 변환은 받는 사람을 뺀 보내는 사람(senderIds)으로만 묻는다
        doReturn(senderSummaries()).when(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.markNotReceived(PARTY_ID, PAYER_ID, PAYER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
        verify(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));
        verifyNoMoreInteractions(userAccess);
        FareSplitTransfer payerTransfer = split.findTransfer(PAYER_ID).orElseThrow();
        assertThat(payerTransfer.status()).as("받는 사람의 줄은 그대로 CONFIRMED").isEqualTo(TransferStatus.CONFIRMED);
        assertThat(payerTransfer.getSentAt()).isEqualTo(CREATED_AT);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, notifier);
    }

    @DisplayName("markNotReceived - 닫힌 정산이면 FARE_SPLIT_CLOSED 이고 저장하지도 알리지도 않는다")
    @Test
    void markNotReceived_closed() {

        /*
         * settings
         */
        Instant closedAt = CREATED_AT.plus(Duration.ofDays(1));
        FareSplit expired = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, null, FareSplitStatus.EXPIRED, CREATED_AT, closedAt,
                List.of(payerTransfer(6_220),
                        FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, null, 1),
                        FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, null, null, 6)));
        doReturn(Optional.of(expired)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(senderSummaries()).when(userAccess).findMemberSummaries(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.markNotReceived(PARTY_ID, PAYER_ID, SENDER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_CLOSED);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor);
    }

    @DisplayName("markNotReceived - 받는 사람이 아니면(보내는 사람은 NOT_FARE_SPLIT_PAYER, 정산에 없는 사람은 NOT_FARE_SPLIT_PARTICIPANT) 사용자 정보를 묻기 전에 막고 저장하지 않는다")
    @Test
    void markNotReceived_notPayer() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable sender = catchThrowable(() -> service.markNotReceived(PARTY_ID, OTHER_SENDER_ID, SENDER_PUBLIC_ID));
        Throwable outsider = catchThrowable(() -> service.markNotReceived(PARTY_ID, OUTSIDER_ID, SENDER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(sender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(outsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        verifyNoInteractions(userAccess, afterCommitExecutor);
        verify(fareSplits, never()).save(any());
    }

    @DisplayName("markNotReceived - 정산이 없으면 FARE_SPLIT_NOT_FOUND")
    @Test
    void markNotReceived_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.markNotReceived(PARTY_ID, PAYER_ID, SENDER_PUBLIC_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_NOT_FOUND);
        verifyNoInteractions(userAccess, afterCommitExecutor);
    }

    // ───────────────────────── edit ─────────────────────────

    @DisplayName("edit - 정산 행을 잠근 뒤 받는 사람을 포함한 참여자 전원의 publicId 를 userId 로 바꿔 택시비·계좌·사람마다 부담 금액을 고쳐 저장하고 받는 사람 화면을 돌려준다. 받는 사람 줄의 금액도 바뀐다")
    @Test
    void edit() {

        /*
         * settings
         */
        doReturn(Optional.of(notStartedSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        // publicId 변환과 응답이 같은 참여자 목록으로 두 번 묻는다
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        doReturn(Map.of(SENDER_ID, "김*수")).when(maskedRealNames).find(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.edit(PARTY_ID, PAYER_ID, editRequest());

        /*
         * verify
         */
        InOrder order = inOrder(fareSplits, userAccess);
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        order.verify(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        order.verify(fareSplits).save(captor.capture());
        order.verify(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        verify(userAccess, times(2)).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        verifyNoMoreInteractions(userAccess);

        FareSplit saved = captor.getValue();
        assertThat(saved.getTotalFare()).isEqualTo(20_000);
        assertThat(saved.findTransfer(SENDER_ID).orElseThrow().getPayment()).isEqualTo(8_000);
        assertThat(saved.findTransfer(OTHER_SENDER_ID).orElseThrow().getPayment()).isEqualTo(5_000);
        FareSplitTransfer payerTransfer = saved.findTransfer(PAYER_ID).orElseThrow();
        assertThat(payerTransfer.getPayment()).as("받는 사람 줄의 금액도 바뀐다").isEqualTo(7_000);
        assertThat(payerTransfer.status()).as("금액만 바뀌고 CONFIRMED 는 그대로").isEqualTo(TransferStatus.CONFIRMED);
        assertThat(payerTransfer.getRequestCount()).isZero();
        assertThat(saved.getBank()).isEqualTo(Bank.SHINHAN);
        assertThat(saved.getAccountNumber()).isEqualTo(new AccountNumber("110123456789"));

        assertThat(detail.myRole()).isEqualTo(FareSplitRole.PAYER);
        assertThat(detail.totalFare()).isEqualTo(20_000);
        assertThat(detail.myAmount()).isEqualTo(7_000);
        assertThat(detail.bank().code()).isEqualTo(Bank.SHINHAN);
        assertThat(detail.accountNumber()).isEqualTo("110123456789");
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::amount).as("받는 사람의 줄은 싣지 않는다")
                .containsExactly(8_000, 5_000);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::maskedRealName)
                .containsExactly("김*수", null);
        verifyNoInteractions(afterCommitExecutor, notifier);
    }

    @DisplayName("edit - 저장소가 줄을 userId 순으로 돌려줘도 응답의 transfers 는 방 창구가 알려 준 합류 순이고 받는 사람은 없다. 방은 한 번만 읽는다")
    @Test
    void edit_joinOrder() {

        /*
         * settings
         */
        doReturn(Optional.of(notStartedSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(Optional.of(crossedRiders())).when(partyAccess).findRiders(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));
        doReturn(Map.of(SENDER_ID, "김*수")).when(maskedRealNames).find(eq(List.of(SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        FareSplitDetailResponse detail = service.edit(PARTY_ID, PAYER_ID, editRequest());

        /*
         * verify
         */
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId)
                .containsExactly(OTHER_SENDER_PUBLIC_ID, SENDER_PUBLIC_ID);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::amount)
                .containsExactly(5_000, 8_000);
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::maskedRealName)
                .containsExactly(null, "김*수");
        verify(partyAccess, times(1)).findRiders(eq(PARTY_ID));
        verifyNoMoreInteractions(partyAccess);
    }

    @DisplayName("edit - 계좌번호가 잘못되면 INVALID_ACCOUNT_NUMBER 이고 정산을 잠그지 않는다")
    @Test
    void edit_invalidAccount() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID,
                request(20_000, Bank.SHINHAN, "12345", payment(PAYER_PUBLIC_ID, 7_000), payment(SENDER_PUBLIC_ID, 8_000), payment(OTHER_SENDER_PUBLIC_ID, 5_000))));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.INVALID_ACCOUNT_NUMBER);
        verifyNoInteractions(fareSplits, userAccess, maskedRealNames);
    }

    @DisplayName("edit - 보내는 사람 누군가 보냈다고 눌렀으면 FARE_SPLIT_ALREADY_STARTED 이고 받는 사람 줄을 포함해 아무 금액도 바꾸지 않고 저장하지 않는다")
    @Test
    void edit_alreadyStarted() {

        /*
         * settings
         */
        FareSplit split = openSplit();
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID, editRequest()));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_ALREADY_STARTED);
        assertThat(split.getTotalFare()).isEqualTo(18_700);
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::getPayment).containsExactly(6_220, 6_240, 6_240);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(partyAccess, maskedRealNames);
    }

    @DisplayName("edit - 받는 사람이 아니면(보내는 사람은 NOT_FARE_SPLIT_PAYER, 정산에 없는 사람은 NOT_FARE_SPLIT_PARTICIPANT) 사용자 정보를 묻기 전에 막고 저장하지 않는다")
    @Test
    void edit_notPayer() {

        /*
         * settings
         */
        doReturn(Optional.of(notStartedSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable sender = catchThrowable(() -> service.edit(PARTY_ID, SENDER_ID, editRequest()));
        Throwable outsider = catchThrowable(() -> service.edit(PARTY_ID, OUTSIDER_ID, editRequest()));

        /*
         * verify
         */
        assertBusinessException(sender, FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
        assertBusinessException(outsider, FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(partyAccess, userAccess, maskedRealNames);
    }

    @DisplayName("edit - 정산 참여자가 아닌 publicId 는 서비스가, 같은 사람이 두 번 오면 도메인이 PAYMENT_TARGET_MISMATCH 로 막고 금액을 바꾸지도 저장하지도 않는다")
    @Test
    void edit_paymentTargetNotParticipantOrDuplicated() {

        /*
         * settings
         */
        FareSplit split = notStartedSplit();
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        Throwable outsider = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID, request(20_000, Bank.SHINHAN, RAW_ACCOUNT,
                payment(PAYER_PUBLIC_ID, 7_000), payment(SENDER_PUBLIC_ID, 8_000), payment(OUTSIDER_PUBLIC_ID, 5_000))));
        Throwable duplicated = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID, request(20_000, Bank.SHINHAN, RAW_ACCOUNT,
                payment(PAYER_PUBLIC_ID, 7_000), payment(SENDER_PUBLIC_ID, 8_000), payment(OTHER_SENDER_PUBLIC_ID, 5_000), payment(OTHER_SENDER_PUBLIC_ID, 5_000))));

        /*
         * verify
         */
        assertBusinessException(outsider, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(duplicated, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertThat(split.getTotalFare()).isEqualTo(18_700);
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::getPayment).containsExactly(6_220, 6_240, 6_240);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(partyAccess, maskedRealNames);
    }

    @DisplayName("edit - 참여자가 한 명이라도 빠지면(보내는 사람이든 받는 사람이든) 도메인이 PAYMENT_TARGET_MISMATCH 로 막고 금액을 바꾸지도 저장하지도 않는다")
    @Test
    void edit_paymentMissing() {

        /*
         * settings
         */
        FareSplit split = notStartedSplit();
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        // 둘 다 합은 택시비와 같다 - 빠진 사람이 있는지를 먼저 본다
        Throwable senderMissing = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID,
                request(20_000, Bank.SHINHAN, RAW_ACCOUNT, payment(PAYER_PUBLIC_ID, 12_000), payment(OTHER_SENDER_PUBLIC_ID, 8_000))));
        Throwable payerMissing = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID,
                request(20_000, Bank.SHINHAN, RAW_ACCOUNT, payment(SENDER_PUBLIC_ID, 12_000), payment(OTHER_SENDER_PUBLIC_ID, 8_000))));

        /*
         * verify
         */
        assertBusinessException(senderMissing, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertBusinessException(payerMissing, FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        assertThat(split.getTotalFare()).isEqualTo(18_700);
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::getPayment).containsExactly(6_220, 6_240, 6_240);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(partyAccess, maskedRealNames);
    }

    @DisplayName("edit - 부담 금액의 합이 택시비와 다르면(1원 많든 적든) 도메인이 PAYMENTS_NOT_EQUAL_FARE 로 막고 금액을 바꾸지도 저장하지도 않는다")
    @Test
    void edit_paymentsNotEqualFare() {

        /*
         * settings
         */
        FareSplit split = notStartedSplit();
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(summaries()).when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID)));

        /*
         * test
         */
        // 7,000 + 8,000 + 5,001 = 20,001 로 택시비보다 1원 많다
        Throwable over = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID, request(20_000, Bank.SHINHAN, RAW_ACCOUNT,
                payment(PAYER_PUBLIC_ID, 7_000), payment(SENDER_PUBLIC_ID, 8_000), payment(OTHER_SENDER_PUBLIC_ID, 5_001))));
        // 7,000 + 8,000 + 4,999 = 19,999 로 택시비보다 1원 적다
        Throwable under = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID, request(20_000, Bank.SHINHAN, RAW_ACCOUNT,
                payment(PAYER_PUBLIC_ID, 7_000), payment(SENDER_PUBLIC_ID, 8_000), payment(OTHER_SENDER_PUBLIC_ID, 4_999))));

        /*
         * verify
         */
        assertBusinessException(over, FareSplitErrorCode.PAYMENTS_NOT_EQUAL_FARE);
        assertBusinessException(under, FareSplitErrorCode.PAYMENTS_NOT_EQUAL_FARE);
        assertThat(split.getTotalFare()).isEqualTo(18_700);
        assertThat(split.getTransfers()).extracting(FareSplitTransfer::getPayment).containsExactly(6_220, 6_240, 6_240);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(partyAccess, maskedRealNames);
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
        Throwable thrown = catchThrowable(() -> service.edit(PARTY_ID, PAYER_ID, editRequest()));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_NOT_FOUND);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(userAccess);
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
                new OpenTransfer(otherPartyId, otherPayerId, 9_350, null, null, CREATED_AT.plusSeconds(600)),
                new OpenTransfer(300L, PAYER_ID, 4_000, SENT_AT, null, CREATED_AT.plusSeconds(300)),
                new OpenTransfer(PARTY_ID, PAYER_ID, 6_240, null, null, CREATED_AT)))
                .when(fareSplits).findOpenTransfers(eq(SENDER_ID));
        doReturn(Map.of(otherPartyId, new PartyPlaces("판교역", "강남역"), PARTY_ID, new PartyPlaces("강남역", "판교역")))
                .when(partyAccess).findPlaces(eq(List.of(otherPartyId, PARTY_ID)));
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
        assertThat(response.fareSplits().get(1).departure()).isEqualTo("강남역");
        assertThat(response.fareSplits().get(1).destination()).isEqualTo("판교역");
        assertThat(response.fareSplits().get(1).payer().nickname()).isEqualTo("받는사람");
    }

    @DisplayName("findMine - status=SENT 면 보냈다고 누른 정산만 준다")
    @Test
    void findMine_sent() {

        /*
         * settings
         */
        doReturn(List.of(
                new OpenTransfer(300L, PAYER_ID, 4_000, SENT_AT, null, CREATED_AT.plusSeconds(300)),
                new OpenTransfer(PARTY_ID, PAYER_ID, 6_240, null, null, CREATED_AT)))
                .when(fareSplits).findOpenTransfers(eq(SENDER_ID));
        doReturn(Map.of(300L, new PartyPlaces("서울역", "홍대입구역"))).when(partyAccess).findPlaces(eq(List.of(300L)));
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
        assertThat(response.fareSplits().get(0).departure()).isEqualTo("서울역");
        assertThat(response.fareSplits().get(0).destination()).isEqualTo("홍대입구역");
    }

    @DisplayName("findMine - 방 이름을 찾지 못한 방은 출발지·도착지가 null 이고 나머지는 그대로 준다")
    @Test
    void findMine_noPlaces() {

        /*
         * settings
         */
        doReturn(List.of(new OpenTransfer(PARTY_ID, PAYER_ID, 6_240, null, null, CREATED_AT)))
                .when(fareSplits).findOpenTransfers(eq(SENDER_ID));
        doReturn(Map.of()).when(partyAccess).findPlaces(eq(List.of(PARTY_ID)));
        doReturn(Map.of(PAYER_ID, new MemberSummary(PAYER_PUBLIC_ID, "받는사람", null)))
                .when(userAccess).findMemberSummaries(eq(List.of(PAYER_ID)));

        /*
         * test
         */
        MyFareSplitListResponse response = service.findMine(SENDER_ID, TransferStatus.NOT_SENT);

        /*
         * verify
         */
        MyFareSplitListResponse.MyFareSplitItem item = response.fareSplits().getFirst();
        assertThat(item.partyId()).isEqualTo(PARTY_ID);
        assertThat(item.departure()).isNull();
        assertThat(item.destination()).isNull();
        assertThat(item.payer().nickname()).isEqualTo("받는사람");
        assertThat(item.myAmount()).isEqualTo(6_240);
    }

    @DisplayName("findMine - 해당하는 정산이 없으면 빈 목록이고 사용자 정보·방 이름을 묻지 않는다")
    @Test
    void findMine_empty() {

        /*
         * settings
         */
        doReturn(List.of(new OpenTransfer(300L, PAYER_ID, 4_000, SENT_AT, null, CREATED_AT)))
                .when(fareSplits).findOpenTransfers(eq(SENDER_ID));

        /*
         * test
         */
        MyFareSplitListResponse response = service.findMine(SENDER_ID, TransferStatus.NOT_SENT);

        /*
         * verify
         */
        assertThat(response.fareSplits()).isEmpty();
        verifyNoInteractions(userAccess, partyAccess);
    }

    // ───────────────────────── expire ─────────────────────────

    @DisplayName("expire - 정산 행을 잠근 뒤 시작 1일이 지났으면 EXPIRED·닫힌 시각·계좌 삭제를 저장한다")
    @Test
    void expire() {

        /*
         * settings
         */
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        Instant now = CREATED_AT.plus(Duration.ofDays(1));

        /*
         * test
         */
        service.expire(PARTY_ID, now);

        /*
         * verify
         */
        InOrder order = inOrder(fareSplits);
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        ArgumentCaptor<FareSplit> captor = ArgumentCaptor.forClass(FareSplit.class);
        order.verify(fareSplits).save(captor.capture());

        FareSplit saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(FareSplitStatus.EXPIRED);
        assertThat(saved.getClosedAt()).isEqualTo(now);
        assertThat(saved.getAccountNumber()).isNull();
        verifyNoInteractions(partyAccess, afterCommitExecutor, notifier);
    }

    @DisplayName("expire - 잠가 보니 아직 1일 전이거나 이미 닫혔으면 저장하지 않는다")
    @Test
    void expire_notDue() {

        /*
         * settings
         */
        FareSplit completed = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, null, FareSplitStatus.COMPLETED, CREATED_AT,
                CREATED_AT.plus(Duration.ofDays(1)), List.of(payerTransfer(12_460), FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, SENT_AT, 1)));
        doReturn(Optional.of(openSplit())).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(Optional.of(completed)).when(fareSplits).findByPartyIdForUpdate(eq(200L));

        /*
         * test
         */
        service.expire(PARTY_ID, CREATED_AT.plus(Duration.ofDays(1)).minusSeconds(1));
        service.expire(200L, CREATED_AT.plus(Duration.ofDays(3)));

        /*
         * verify
         */
        verify(fareSplits, never()).save(any());
    }

    @DisplayName("expire - 정산이 없으면 건너뛰지 않고 FARE_SPLIT_NOT_FOUND")
    @Test
    void expire_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.expire(PARTY_ID, CREATED_AT.plus(Duration.ofDays(3))));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_NOT_FOUND);
        verify(fareSplits, never()).save(any());
    }

    // ───────────────────────── forgetPayerAccounts ─────────────────────────

    @DisplayName("forgetPayerAccounts - 받는 사람의 OPEN 정산마다 정산 행을 잠근 뒤 계좌번호를 지워 저장하고 지운 수를 돌려준다")
    @Test
    void forgetPayerAccounts() {

        /*
         * settings
         */
        FareSplit first = openSplit();
        FareSplit second = FareSplit.load(200L, PAYER_ID, 10_000, Bank.KB, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(payerTransfer(5_000), FareSplitTransfer.load(SENDER_ID, 5_000, null, null, 1)));
        doReturn(List.of(PARTY_ID, 200L)).when(fareSplits).findOpenByPayer(eq(PAYER_ID));
        doReturn(Optional.of(first)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        doReturn(Optional.of(second)).when(fareSplits).findByPartyIdForUpdate(eq(200L));

        /*
         * test
         */
        int cleared = service.forgetPayerAccounts(PAYER_ID);

        /*
         * verify
         */
        assertThat(cleared).isEqualTo(2);
        InOrder order = inOrder(fareSplits);
        order.verify(fareSplits).findOpenByPayer(eq(PAYER_ID));
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        order.verify(fareSplits).save(eq(first));
        order.verify(fareSplits).findByPartyIdForUpdate(eq(200L));
        order.verify(fareSplits).save(eq(second));
        assertThat(first.getAccountNumber()).isNull();
        assertThat(second.getAccountNumber()).isNull();
        assertThat(first.getStatus()).as("정산은 열어 둔다").isEqualTo(FareSplitStatus.OPEN);
        verifyNoInteractions(partyAccess);
    }

    @DisplayName("forgetPayerAccounts - 이미 지운 정산은 저장하지 않는다(다시 불러도 된다)")
    @Test
    void forgetPayerAccounts_idempotent() {

        /*
         * settings
         */
        FareSplit cleared = openSplit();
        cleared.forgetAccount();
        doReturn(List.of(PARTY_ID)).when(fareSplits).findOpenByPayer(eq(PAYER_ID));
        doReturn(Optional.of(cleared)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        int count = service.forgetPayerAccounts(PAYER_ID);

        /*
         * verify
         */
        assertThat(count).isZero();
        verify(fareSplits, times(1)).findByPartyIdForUpdate(anyLong());
        verify(fareSplits, never()).save(any());
    }

    @DisplayName("forgetPayerAccounts - 찾아 둔 정산이 잠글 때 없으면 건너뛰지 않고 FARE_SPLIT_NOT_FOUND")
    @Test
    void forgetPayerAccounts_notFound() {

        /*
         * settings
         */
        doReturn(List.of(PARTY_ID)).when(fareSplits).findOpenByPayer(eq(PAYER_ID));
        doReturn(Optional.empty()).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.forgetPayerAccounts(PAYER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, FareSplitErrorCode.FARE_SPLIT_NOT_FOUND);
        verify(fareSplits, never()).save(any());
    }

    @DisplayName("forgetPayerAccounts - 받는 사람인 OPEN 정산이 없으면 아무것도 잠그지 않는다")
    @Test
    void forgetPayerAccounts_none() {

        /*
         * settings
         */
        doReturn(List.of()).when(fareSplits).findOpenByPayer(eq(OUTSIDER_ID));

        /*
         * test
         */
        int count = service.forgetPayerAccounts(OUTSIDER_ID);

        /*
         * verify
         */
        assertThat(count).isZero();
        verify(fareSplits, never()).findByPartyIdForUpdate(anyLong());
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

    /** 받는 사람(방장)의 줄 - 정산을 시작할 때 CONFIRMED, 요청 횟수 0 으로 생긴다 */
    private static FareSplitTransfer payerTransfer(int payment) {
        return FareSplitTransfer.load(PAYER_ID, payment, CREATED_AT, CREATED_AT, 0);
    }

    /** 택시비 18,700원을 받는 사람 6,220원, 보내는 사람 둘이 6,240원씩 부담하는 OPEN 정산. 아무도 보내기 전 */
    private static FareSplit notStartedSplit() {
        return FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(payerTransfer(6_220),
                        FareSplitTransfer.load(SENDER_ID, 6_240, null, null, 1),
                        FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, null, null, 1)));
    }

    /** 택시비 18,700원을 받는 사람(방장) 6,220원, 보내는 사람 둘이 6,240원씩 부담하는 정산을 시작하는 요청 */
    private static FareSplitRequest evenRequest() {
        return request(18_700, Bank.KAKAO, RAW_ACCOUNT,
                payment(PAYER_PUBLIC_ID, 6_220), payment(SENDER_PUBLIC_ID, 6_240), payment(OTHER_SENDER_PUBLIC_ID, 6_240));
    }

    /** 택시비 20,000원을 받는 사람(방장) 7,000원, 보내는 사람이 8,000원·5,000원 부담하도록 고치는 요청 */
    private static FareSplitRequest editRequest() {
        return request(20_000, Bank.SHINHAN, "110-123-456789",
                payment(PAYER_PUBLIC_ID, 7_000), payment(SENDER_PUBLIC_ID, 8_000), payment(OTHER_SENDER_PUBLIC_ID, 5_000));
    }

    private static FareSplitRequest request(int totalFare, Bank bank, String accountNumber, FareSplitRequest.Payment... payments) {
        return new FareSplitRequest(totalFare, bank, accountNumber, List.of(payments));
    }

    private static FareSplitRequest.Payment payment(UUID userPublicId, int amount) {
        return new FareSplitRequest.Payment(userPublicId, amount);
    }

    /** 받는 사람(방장)의 줄은 CONFIRMED, 첫 번째 보내는 사람은 SENT, 두 번째는 NOT_SENT */
    private static FareSplit openSplit() {
        return FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(payerTransfer(6_220),
                        FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, null, 1),
                        FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, null, null, 1)));
    }

    /** 셋 다 도착해 탄 끝난 방. 방장은 payer, 합류 순 payer → sender → other sender */
    private static PartyRiders finishedRiders() {
        return new PartyRiders(PARTY_ID, PAYER_ID, true, List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID), List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID));
    }

    /** 셋 다 도착해 탄 끝난 방. 방장은 payer, 합류 순 other sender → payer → sender 로 userId 순과 다르다 */
    private static PartyRiders crossedRiders() {
        List<Long> joinOrder = List.of(OTHER_SENDER_ID, PAYER_ID, SENDER_ID);

        return new PartyRiders(PARTY_ID, PAYER_ID, true, joinOrder, joinOrder);
    }

    /** 저장소(FareSplitJpa)처럼 줄을 userId 순으로 담아 돌려준다 */
    private static FareSplit inUserIdOrder(FareSplit split) {
        return FareSplit.load(split.getPartyId(), split.getPayerId(), split.getTotalFare(), split.getBank(),
                split.getAccountNumber(), split.getStatus(), split.getCreatedAt(), split.getClosedAt(),
                split.getTransfers().stream().sorted(Comparator.comparing(FareSplitTransfer::getMemberId)).toList());
    }

    private Runnable capturedTask(String name) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommitExecutor).execute(eq(name), captor.capture());

        return captor.getValue();
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
