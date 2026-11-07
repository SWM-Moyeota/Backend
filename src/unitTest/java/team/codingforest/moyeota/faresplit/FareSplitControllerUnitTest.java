package team.codingforest.moyeota.faresplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.faresplit.domain.Bank;
import team.codingforest.moyeota.faresplit.domain.FareSplitRole;
import team.codingforest.moyeota.faresplit.domain.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.TransferStatus;
import team.codingforest.moyeota.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.faresplit.dto.FareSplitDetailResponse.FareSplitBank;
import team.codingforest.moyeota.faresplit.dto.FareSplitDetailResponse.FareSplitPayer;
import team.codingforest.moyeota.faresplit.dto.FareSplitDetailResponse.FareSplitTransferItem;
import team.codingforest.moyeota.faresplit.dto.MyFareSplitListResponse;
import team.codingforest.moyeota.faresplit.dto.MyFareSplitListResponse.MyFareSplitItem;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FareSplitController.class)
class FareSplitControllerUnitTest extends ControllerUnitTestSupport {
    private static final String URL = "/api/v1/matching/rooms/{partyId}/fare-split";
    private static final String MY_URL = "/api/v1/users/me/fare-splits";
    private static final Long PARTY_ID = 100L;
    private static final UUID SENDER_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-11-03T12:40:00Z");
    private static final Instant SENT_AT = Instant.parse("2026-11-03T12:45:00Z");
    private static final String DEEPLINK = "supertoss://send?amount=6240&bank=%EC%B9%B4%EC%B9%B4%EC%98%A4%EB%B1%85%ED%81%AC&accountNo=3333012345678";
    private static final String BODY = "{\"totalFare\": 18700, \"bankCode\": \"KAKAO\", \"accountNumber\": \"3333-01-2345678\"}";

    @MockitoBean
    private FareSplitService fareSplitService;

    // ───────────────────────── POST /fare-split ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split - 토큰의 사용자를 받는 사람으로 넘기고 201 과 정산 상세를 준다")
    @Test
    void create() throws Exception {

        /*
         * settings
         */
        doReturn(payerView()).when(fareSplitService).create(eq(PARTY_ID), eq(USER_ID), eq(18_700), eq(Bank.KAKAO), eq("3333-01-2345678"));

        /*
         * test
         */
        mockMvc.perform(post(URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(BODY))

        /*
         * verify
         */
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partyId").value(PARTY_ID))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.totalFare").value(18_700))
                .andExpect(jsonPath("$.amountPerPerson").value(6_240))
                .andExpect(jsonPath("$.payer.publicId").value(PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.payer.nickname").value("받는사람"))
                .andExpect(jsonPath("$.myRole").value("PAYER"))
                .andExpect(jsonPath("$.myAmount").value(6_220))
                .andExpect(jsonPath("$.bank.code").value("KAKAO"))
                .andExpect(jsonPath("$.bank.name").value("카카오뱅크"))
                .andExpect(jsonPath("$.accountNumber").value("3333012345678"))
                .andExpect(jsonPath("$.tossDeeplink").value(nullValue()))
                .andExpect(jsonPath("$.transfers", hasSize(1)))
                .andExpect(jsonPath("$.transfers[0].publicId").value(SENDER_PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.transfers[0].nickname").value("보낸사람"))
                .andExpect(jsonPath("$.transfers[0].status").value("SENT"))
                .andExpect(jsonPath("$.transfers[0].sentAt").value("2026-11-03T12:45:00Z"))
                .andExpect(jsonPath("$.transfers[0].confirmedAt").value(nullValue()))
                .andExpect(jsonPath("$.transfers[0].maskedRealName").value("김*수"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-03T12:40:00Z"))
                .andExpect(jsonPath("$.closedAt").value(nullValue()));
        verify(fareSplitService).create(eq(PARTY_ID), eq(USER_ID), eq(18_700), eq(Bank.KAKAO), eq("3333-01-2345678"));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split - 필드가 없거나 비었으면 400 INVALID_REQUEST 이고 서비스를 부르지 않는다")
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"bankCode\": \"KAKAO\", \"accountNumber\": \"3333012345678\"}",
            "{\"totalFare\": 18700, \"accountNumber\": \"3333012345678\"}",
            "{\"totalFare\": 18700, \"bankCode\": \"KAKAO\"}",
            "{\"totalFare\": 18700, \"bankCode\": \"KAKAO\", \"accountNumber\": \"  \"}",
            "{}"})
    void create_missingField(String body) throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(fareSplitService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split - 없는 은행 코드면 400 INVALID_REQUEST")
    @Test
    void create_unknownBank() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalFare\": 18700, \"bankCode\": \"MOYEOTA\", \"accountNumber\": \"3333012345678\"}"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(fareSplitService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split - 서비스 예외를 상태 코드와 code 로 돌려준다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = FareSplitErrorCode.class, names = {"INVALID_FARE", "INVALID_ACCOUNT_NUMBER", "NOT_PARTY_MEMBER", "NOT_A_RIDER",
            "PARTY_NOT_FOUND", "PARTY_NOT_FINISHED", "NOT_ENOUGH_RIDERS", "FARE_SPLIT_ALREADY_EXISTS"})
    void create_businessError(FareSplitErrorCode errorCode) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(fareSplitService).create(anyLong(), anyLong(), anyInt(), any(), anyString());

        /*
         * test
         */
        mockMvc.perform(post(URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(BODY))

        /*
         * verify
         */
                .andExpect(status().is(errorCode.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split - 토큰이 없으면 401")
    @Test
    void create_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(URL, PARTY_ID).contentType(MediaType.APPLICATION_JSON).content(BODY))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(fareSplitService);
    }

    // ───────────────────────── GET /fare-split ─────────────────────────

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/fare-split - 토큰의 사용자 기준 상세를 준다(보내는 사람은 딥링크가 있고 가린 실명은 없다)")
    @Test
    void detail() throws Exception {

        /*
         * settings
         */
        doReturn(senderView()).when(fareSplitService).getDetail(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(URL, PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("SENDER"))
                .andExpect(jsonPath("$.myAmount").value(6_240))
                .andExpect(jsonPath("$.accountNumber").value("3333012345678"))
                .andExpect(jsonPath("$.tossDeeplink").value(DEEPLINK))
                .andExpect(jsonPath("$.transfers[0].maskedRealName").value(nullValue()));
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/fare-split - 서비스 예외를 상태 코드와 code 로 돌려준다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = FareSplitErrorCode.class, names = {"FARE_SPLIT_NOT_FOUND", "NOT_FARE_SPLIT_PARTICIPANT"})
    void detail_businessError(FareSplitErrorCode errorCode) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(fareSplitService).getDetail(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(URL, PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().is(errorCode.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/fare-split - 토큰이 없으면 401")
    @Test
    void detail_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(URL, PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(fareSplitService);
    }


    // ───────────────────────── PATCH /fare-split ─────────────────────────

    @DisplayName("PATCH /api/v1/matching/rooms/{partyId}/fare-split - 토큰의 사용자와 본문을 넘기고 200 과 고친 정산 상세를 준다")
    @Test
    void edit() throws Exception {

        /*
         * settings
         */
        doReturn(payerView()).when(fareSplitService).edit(eq(PARTY_ID), eq(USER_ID), eq(18_700), eq(Bank.KAKAO), eq("3333-01-2345678"));

        /*
         * test
         */
        mockMvc.perform(patch(URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(BODY))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyId").value(PARTY_ID))
                .andExpect(jsonPath("$.myRole").value("PAYER"))
                .andExpect(jsonPath("$.totalFare").value(18_700))
                .andExpect(jsonPath("$.amountPerPerson").value(6_240))
                .andExpect(jsonPath("$.accountNumber").value("3333012345678"));
        verify(fareSplitService).edit(eq(PARTY_ID), eq(USER_ID), eq(18_700), eq(Bank.KAKAO), eq("3333-01-2345678"));
    }

    @DisplayName("PATCH /api/v1/matching/rooms/{partyId}/fare-split - 세 필드 중 하나라도 없으면 400 INVALID_REQUEST 이고 서비스를 부르지 않는다")
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"bankCode\": \"KAKAO\", \"accountNumber\": \"3333012345678\"}",
            "{\"totalFare\": 18700, \"accountNumber\": \"3333012345678\"}",
            "{\"totalFare\": 18700, \"bankCode\": \"KAKAO\"}",
            "{\"totalFare\": 18700, \"bankCode\": \"MOYEOTA\", \"accountNumber\": \"3333012345678\"}"})
    void edit_missingField(String body) throws Exception {

        /*
         * test
         */
        mockMvc.perform(patch(URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(fareSplitService);
    }

    @DisplayName("PATCH /api/v1/matching/rooms/{partyId}/fare-split - 서비스 예외를 상태 코드와 code 로 돌려준다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = FareSplitErrorCode.class, names = {"INVALID_FARE", "INVALID_ACCOUNT_NUMBER", "NOT_FARE_SPLIT_PARTICIPANT", "NOT_FARE_SPLIT_PAYER",
            "FARE_SPLIT_NOT_FOUND", "FARE_SPLIT_CLOSED", "FARE_SPLIT_ALREADY_STARTED"})
    void edit_businessError(FareSplitErrorCode errorCode) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(fareSplitService).edit(anyLong(), anyLong(), anyInt(), any(), anyString());

        /*
         * test
         */
        mockMvc.perform(patch(URL, PARTY_ID).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(BODY))

        /*
         * verify
         */
                .andExpect(status().is(errorCode.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }

    // ───────────────────────── POST /fare-split/cancel ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split/cancel - 토큰의 사용자로 취소하고 204")
    @Test
    void cancel() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(URL + "/cancel", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(fareSplitService).cancel(eq(PARTY_ID), eq(USER_ID));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split/cancel - 서비스 예외를 상태 코드와 code 로 돌려준다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = FareSplitErrorCode.class, names = {"NOT_FARE_SPLIT_PARTICIPANT", "NOT_FARE_SPLIT_PAYER", "FARE_SPLIT_NOT_FOUND",
            "FARE_SPLIT_CLOSED", "FARE_SPLIT_ALREADY_STARTED"})
    void cancel_businessError(FareSplitErrorCode errorCode) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(fareSplitService).cancel(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post(URL + "/cancel", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().is(errorCode.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    // ───────────────────────── POST /fare-split/sent ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split/sent - 토큰의 사용자를 보내는 사람으로 넘기고 204")
    @Test
    void markSent() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(URL + "/sent", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(fareSplitService).markSent(eq(PARTY_ID), eq(USER_ID));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split/sent - 서비스 예외를 상태 코드와 code 로 돌려준다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = FareSplitErrorCode.class, names = {"NOT_FARE_SPLIT_PARTICIPANT", "NOT_FARE_SPLIT_SENDER", "FARE_SPLIT_NOT_FOUND", "FARE_SPLIT_CLOSED"})
    void markSent_businessError(FareSplitErrorCode errorCode) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(fareSplitService).markSent(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post(URL + "/sent", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().is(errorCode.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    // ───────────────────────── POST /fare-split/transfers/{publicId}/received·not-received ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split/transfers/{publicId}/received - 토큰의 사용자를 받는 사람으로, 경로의 publicId 를 보내는 사람으로 넘기고 204")
    @Test
    void confirmReceived() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(URL + "/transfers/{publicId}/received", PARTY_ID, SENDER_PUBLIC_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(fareSplitService).confirmReceived(eq(PARTY_ID), eq(USER_ID), eq(SENDER_PUBLIC_ID));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split/transfers/{publicId}/received - 서비스 예외를 상태 코드와 code 로 돌려준다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = FareSplitErrorCode.class, names = {"NOT_FARE_SPLIT_PARTICIPANT", "NOT_FARE_SPLIT_PAYER", "FARE_SPLIT_NOT_FOUND",
            "FARE_SPLIT_TRANSFER_NOT_FOUND", "FARE_SPLIT_CLOSED"})
    void confirmReceived_businessError(FareSplitErrorCode errorCode) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(fareSplitService).confirmReceived(eq(PARTY_ID), eq(USER_ID), eq(SENDER_PUBLIC_ID));

        /*
         * test
         */
        mockMvc.perform(post(URL + "/transfers/{publicId}/received", PARTY_ID, SENDER_PUBLIC_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().is(errorCode.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split/transfers/{publicId}/not-received - 토큰의 사용자를 받는 사람으로, 경로의 publicId 를 보내는 사람으로 넘기고 204")
    @Test
    void markNotReceived() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(URL + "/transfers/{publicId}/not-received", PARTY_ID, SENDER_PUBLIC_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(fareSplitService).markNotReceived(eq(PARTY_ID), eq(USER_ID), eq(SENDER_PUBLIC_ID));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/fare-split/transfers/{publicId}/not-received - 서비스 예외를 상태 코드와 code 로 돌려준다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = FareSplitErrorCode.class, names = {"NOT_FARE_SPLIT_PARTICIPANT", "NOT_FARE_SPLIT_PAYER", "FARE_SPLIT_NOT_FOUND",
            "FARE_SPLIT_TRANSFER_NOT_FOUND", "FARE_SPLIT_CLOSED", "TRANSFER_NOT_SENT"})
    void markNotReceived_businessError(FareSplitErrorCode errorCode) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(fareSplitService).markNotReceived(eq(PARTY_ID), eq(USER_ID), eq(SENDER_PUBLIC_ID));

        /*
         * test
         */
        mockMvc.perform(post(URL + "/transfers/{publicId}/not-received", PARTY_ID, SENDER_PUBLIC_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().is(errorCode.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @DisplayName("POST .../transfers/{publicId}/received·not-received - publicId 가 UUID 가 아니면 400 INVALID_REQUEST 이고 서비스를 부르지 않는다")
    @ParameterizedTest
    @ValueSource(strings = {"received", "not-received"})
    void transfer_invalidPublicId(String action) throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(URL + "/transfers/{publicId}/" + action, PARTY_ID, "not-a-uuid").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(fareSplitService);
    }

    @DisplayName("PATCH·POST 송금 처리·취소 - 토큰이 없으면 401 이고 서비스를 부르지 않는다")
    @ParameterizedTest
    @ValueSource(strings = {"PATCH ", "POST /cancel", "POST /sent", "POST /transfers/00000000-0000-0000-0000-000000000002/received",
            "POST /transfers/00000000-0000-0000-0000-000000000002/not-received"})
    void mutation_noToken(String route) throws Exception {

        /*
         * settings
         */
        String[] parts = route.split(" ", 2);
        String path = URL + parts[1];

        /*
         * test
         */
        mockMvc.perform((parts[0].equals("PATCH") ? patch(path, PARTY_ID) : post(path, PARTY_ID)).contentType(MediaType.APPLICATION_JSON).content(BODY))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(fareSplitService);
    }

    // ───────────────────────── GET /users/me/fare-splits ─────────────────────────

    @DisplayName("GET /api/v1/users/me/fare-splits - status 를 생략하면 NOT_SENT 로 찾고 목록을 준다")
    @Test
    void findMine() throws Exception {

        /*
         * settings
         */
        doReturn(new MyFareSplitListResponse(List.of(new MyFareSplitItem(PARTY_ID, "강남역", "판교역", new FareSplitPayer(SENDER_PUBLIC_ID, "받는사람"),
                6_240, TransferStatus.NOT_SENT, CREATED_AT))))
                .when(fareSplitService).findMine(eq(USER_ID), eq(TransferStatus.NOT_SENT));

        /*
         * test
         */
        mockMvc.perform(get(MY_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fareSplits", hasSize(1)))
                .andExpect(jsonPath("$.fareSplits[0].partyId").value(PARTY_ID))
                .andExpect(jsonPath("$.fareSplits[0].departure").value("강남역"))
                .andExpect(jsonPath("$.fareSplits[0].destination").value("판교역"))
                .andExpect(jsonPath("$.fareSplits[0].payer.publicId").value(SENDER_PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.fareSplits[0].payer.nickname").value("받는사람"))
                .andExpect(jsonPath("$.fareSplits[0].myAmount").value(6_240))
                .andExpect(jsonPath("$.fareSplits[0].status").value("NOT_SENT"))
                .andExpect(jsonPath("$.fareSplits[0].createdAt").value("2026-11-03T12:40:00Z"));
        verify(fareSplitService).findMine(eq(USER_ID), eq(TransferStatus.NOT_SENT));
    }

    @DisplayName("GET /api/v1/users/me/fare-splits - status 를 주면 그 상태로 찾는다")
    @Test
    void findMine_status() throws Exception {

        /*
         * settings
         */
        doReturn(new MyFareSplitListResponse(List.of())).when(fareSplitService).findMine(eq(USER_ID), eq(TransferStatus.SENT));

        /*
         * test
         */
        mockMvc.perform(get(MY_URL).param("status", "SENT").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fareSplits", hasSize(0)));
        verify(fareSplitService).findMine(eq(USER_ID), eq(TransferStatus.SENT));
    }

    @DisplayName("GET /api/v1/users/me/fare-splits - 없는 status 면 400 INVALID_REQUEST 이고 서비스를 부르지 않는다")
    @Test
    void findMine_invalidStatus() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(MY_URL).param("status", "PAID").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(fareSplitService, never()).findMine(anyLong(), any());
    }

    @DisplayName("GET /api/v1/users/me/fare-splits - 토큰이 없으면 401")
    @Test
    void findMine_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(MY_URL))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(fareSplitService);
    }

    // ───────────────────────── fixtures ─────────────────────────

    private static FareSplitDetailResponse payerView() {
        return new FareSplitDetailResponse(PARTY_ID, FareSplitStatus.OPEN, 18_700, 6_240, new FareSplitPayer(PUBLIC_ID, "받는사람"),
                FareSplitRole.PAYER, 6_220, new FareSplitBank(Bank.KAKAO, "카카오뱅크"), "3333012345678", null,
                List.of(new FareSplitTransferItem(SENDER_PUBLIC_ID, "보낸사람", TransferStatus.SENT, SENT_AT, null, "김*수")),
                CREATED_AT, null);
    }

    private static FareSplitDetailResponse senderView() {
        return new FareSplitDetailResponse(PARTY_ID, FareSplitStatus.OPEN, 18_700, 6_240, new FareSplitPayer(SENDER_PUBLIC_ID, "받는사람"),
                FareSplitRole.SENDER, 6_240, new FareSplitBank(Bank.KAKAO, "카카오뱅크"), "3333012345678", DEEPLINK,
                List.of(new FareSplitTransferItem(PUBLIC_ID, "나", TransferStatus.NOT_SENT, null, null, null)),
                CREATED_AT, null);
    }
}
