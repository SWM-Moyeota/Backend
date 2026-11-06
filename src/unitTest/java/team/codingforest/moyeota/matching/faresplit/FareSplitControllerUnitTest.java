package team.codingforest.moyeota.matching.faresplit;

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
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.faresplit.domain.Bank;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitRole;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitStatus;
import team.codingforest.moyeota.matching.faresplit.domain.TransferStatus;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitDetailResponse.FareSplitBank;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitDetailResponse.FareSplitPayer;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitDetailResponse.FareSplitTransferItem;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FareSplitController.class)
class FareSplitControllerUnitTest extends ControllerUnitTestSupport {
    private static final String URL = "/api/v1/matching/rooms/{partyId}/fare-split";
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
    @EnumSource(value = MatchingErrorCode.class, names = {"INVALID_FARE", "INVALID_ACCOUNT_NUMBER", "NOT_PARTY_MEMBER", "NOT_A_RIDER",
            "PARTY_NOT_FOUND", "PARTY_NOT_FINISHED", "NOT_ENOUGH_RIDERS", "FARE_SPLIT_ALREADY_EXISTS"})
    void create_businessError(MatchingErrorCode errorCode) throws Exception {

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
    @EnumSource(value = MatchingErrorCode.class, names = {"FARE_SPLIT_NOT_FOUND", "NOT_FARE_SPLIT_PARTICIPANT"})
    void detail_businessError(MatchingErrorCode errorCode) throws Exception {

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
