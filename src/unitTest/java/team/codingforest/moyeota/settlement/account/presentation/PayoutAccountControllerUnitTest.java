package team.codingforest.moyeota.settlement.account.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.account.application.PayoutAccountService;
import team.codingforest.moyeota.settlement.account.domain.BusinessInfo;
import team.codingforest.moyeota.settlement.account.domain.BusinessType;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;
import team.codingforest.moyeota.settlement.account.dto.PayoutAccountRegisterCommand;
import team.codingforest.moyeota.settlement.account.dto.PayoutAccountResponse;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PayoutAccountController.class, properties = "moyeota.settlement.toss.enabled=true")
class PayoutAccountControllerUnitTest extends ControllerUnitTestSupport {
    private static final String URL = "/api/v1/drivers/me/payout-account";
    private static final String REGISTER_BODY = """
            {"businessType":"CORPORATE","companyName":"모여타택시","representativeName":"홍길동",
             "businessRegistrationNumber":"2208112341","email":"owner@example.com","phone":"0212345678",
             "bankCode":"KOOKMIN","accountNumber":"110123456789","accountHolder":"모여타택시"}""";
    private static final String CHANGE_BODY = """
            {"bankCode":"SHINHAN","accountNumber":"1002003004005","accountHolder":"새예금주"}""";
    private static final PayoutAccountResponse RESPONSE = new PayoutAccountResponse(BusinessType.CORPORATE, BankCode.KOOKMIN, "국민은행",
            "********6789", "모여타택시", SellerStatus.PARTIALLY_APPROVED, true);

    @MockitoBean
    private PayoutAccountService payoutAccountService;

    // ---------- POST /api/v1/drivers/me/payout-account ----------

    @DisplayName("POST /api/v1/drivers/me/payout-account - 토큰의 기사와 사업자 정보·계좌를 묶어 서비스에 넘기고 201 로 가린 계좌를 내려준다")
    @Test
    void register() throws Exception {

        /*
         * settings
         */
        doReturn(RESPONSE).when(payoutAccountService).register(eq(DRIVER_ID), any());

        /*
         * test
         */
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessType").value("CORPORATE"))
                .andExpect(jsonPath("$.bankCode").value("KOOKMIN"))
                .andExpect(jsonPath("$.bankName").value("국민은행"))
                .andExpect(jsonPath("$.maskedAccountNumber").value("********6789"))
                .andExpect(jsonPath("$.accountHolder").value("모여타택시"))
                .andExpect(jsonPath("$.sellerStatus").value("PARTIALLY_APPROVED"))
                .andExpect(jsonPath("$.payable").value(true))
                .andExpect(jsonPath("$.accountNumber").doesNotExist())
                .andExpect(jsonPath("$.sellerId").doesNotExist())
                .andExpect(jsonPath("$.taxiDriverId").doesNotExist());

        ArgumentCaptor<PayoutAccountRegisterCommand> command = ArgumentCaptor.forClass(PayoutAccountRegisterCommand.class);
        verify(payoutAccountService).register(eq(DRIVER_ID), command.capture());
        assertThat(command.getValue()).isEqualTo(new PayoutAccountRegisterCommand(BusinessType.CORPORATE,
                new BusinessInfo("모여타택시", "홍길동", "2208112341", "owner@example.com", "0212345678"),
                BankCode.KOOKMIN, "110123456789", "모여타택시"));
    }

    @DisplayName("POST /api/v1/drivers/me/payout-account - 상호는 생략할 수 있고 null 로 넘어간다")
    @Test
    void register_withoutCompanyName() throws Exception {

        /*
         * settings
         */
        doReturn(RESPONSE).when(payoutAccountService).register(eq(DRIVER_ID), any());

        /*
         * test
         */
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY.replace("\"companyName\":\"모여타택시\",", ""))
                        .with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isCreated());

        ArgumentCaptor<PayoutAccountRegisterCommand> command = ArgumentCaptor.forClass(PayoutAccountRegisterCommand.class);
        verify(payoutAccountService).register(eq(DRIVER_ID), command.capture());
        assertThat(command.getValue().businessInfo().name()).isNull();
    }

    @DisplayName("POST /api/v1/drivers/me/payout-account - 형식이 틀린 필드는 400 INVALID_REQUEST 로 그 필드를 알려주고 서비스를 부르지 않는다")
    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
            "businessType, ",
            "representativeName, ' '",
            "businessRegistrationNumber, 220-81-12341",
            "businessRegistrationNumber, 220811234",
            "email, owner",
            "phone, 02-1234-5678",
            "phone, 0212345",
            "accountNumber, 110-123-456789",
            "accountNumber, 123456789012345",
            "accountHolder, ' '",
            "accountHolder, 가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하"
    })
    void register_invalidField(String field, String value) throws Exception {

        /*
         * settings
         */
        String body = REGISTER_BODY.replaceFirst("\"" + field + "\":\"[^\"]*\"",
                "\"" + field + "\":" + (value == null ? "null" : "\"" + value + "\""));

        /*
         * test
         */
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString(field)));
        verifyNoInteractions(payoutAccountService);
    }

    @DisplayName("POST /api/v1/drivers/me/payout-account - 없는 은행 코드는 본문을 읽을 수 없어 400 INVALID_REQUEST 이고 서비스를 부르지 않는다")
    @Test
    void register_unknownBank() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY.replace("KOOKMIN", "KB")).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(payoutAccountService);
    }

    @DisplayName("POST /api/v1/drivers/me/payout-account - 서비스의 BusinessException 은 그 상태코드와 코드로 내려간다")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PAYOUT_ACCOUNT_REJECTED, 400",
            "PAYOUT_ACCOUNT_ALREADY_REGISTERED, 409",
            "PAYOUT_ACCOUNT_REGISTERING, 409",
            "PAYOUT_PROVIDER_UNAVAILABLE, 502"
    })
    void register_businessException(SettlementErrorCode errorCode, int httpStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(payoutAccountService).register(eq(DRIVER_ID), any());

        /*
         * test
         */
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }

    @DisplayName("POST /api/v1/drivers/me/payout-account - 기사로 등록되지 않은 유저면 404 이고 서비스를 부르지 않는다")
    @Test
    void register_notDriver() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(payoutAccountService);
    }

    @DisplayName("POST /api/v1/drivers/me/payout-account - 토큰이 없으면 401 이고 서비스를 부르지 않는다")
    @Test
    void register_unauthenticated() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(payoutAccountService);
    }

    // ---------- PUT /api/v1/drivers/me/payout-account ----------

    @DisplayName("PUT /api/v1/drivers/me/payout-account - 토큰의 기사와 새 계좌를 서비스에 넘기고 200 으로 바뀐 계좌를 내려준다")
    @Test
    void changeAccount() throws Exception {

        /*
         * settings
         */
        PayoutAccountResponse changed = new PayoutAccountResponse(BusinessType.CORPORATE, BankCode.SHINHAN, "신한은행",
                "*********4005", "새예금주", SellerStatus.APPROVAL_REQUIRED, false);
        doReturn(changed).when(payoutAccountService).changeAccount(eq(DRIVER_ID), eq(BankCode.SHINHAN), eq("1002003004005"), eq("새예금주"));

        /*
         * test
         */
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankCode").value("SHINHAN"))
                .andExpect(jsonPath("$.maskedAccountNumber").value("*********4005"))
                .andExpect(jsonPath("$.sellerStatus").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.payable").value(false));
        verify(payoutAccountService).changeAccount(eq(DRIVER_ID), eq(BankCode.SHINHAN), eq("1002003004005"), eq("새예금주"));
    }

    @DisplayName("PUT /api/v1/drivers/me/payout-account - 형식이 틀린 필드는 400 INVALID_REQUEST 이고 서비스를 부르지 않는다")
    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
            "bankCode, ",
            "accountNumber, 1002-003-004005",
            "accountHolder, ' '"
    })
    void changeAccount_invalidField(String field, String value) throws Exception {

        /*
         * settings
         */
        String body = CHANGE_BODY.replaceFirst("\"" + field + "\":\"[^\"]*\"",
                "\"" + field + "\":" + (value == null ? "null" : "\"" + value + "\""));

        /*
         * test
         */
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString(field)));
        verifyNoInteractions(payoutAccountService);
    }

    @DisplayName("PUT /api/v1/drivers/me/payout-account - 서비스의 BusinessException 은 그 상태코드와 코드로 내려간다")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PAYOUT_ACCOUNT_NOT_FOUND, 404",
            "PAYOUT_ACCOUNT_LOCKED, 409",
            "PAYOUT_PROVIDER_UNAVAILABLE, 502"
    })
    void changeAccount_businessException(SettlementErrorCode errorCode, int httpStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(payoutAccountService).changeAccount(eq(DRIVER_ID), any(), any(), any());

        /*
         * test
         */
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()));
    }

    // ---------- GET /api/v1/drivers/me/payout-account ----------

    @DisplayName("GET /api/v1/drivers/me/payout-account - 토큰의 기사로 조회해 가린 계좌와 셀러 상태를 내려준다")
    @Test
    void find() throws Exception {

        /*
         * settings
         */
        doReturn(RESPONSE).when(payoutAccountService).find(eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedAccountNumber").value("********6789"))
                .andExpect(jsonPath("$.sellerStatus").value("PARTIALLY_APPROVED"))
                .andExpect(jsonPath("$.payable").value(true));
        verify(payoutAccountService).find(eq(DRIVER_ID));
    }

    @DisplayName("GET /api/v1/drivers/me/payout-account - 셀러 등록이 끝나지 않았으면 sellerStatus 가 null 로 내려간다")
    @Test
    void find_unfinished() throws Exception {

        /*
         * settings
         */
        doReturn(new PayoutAccountResponse(BusinessType.CORPORATE, BankCode.KOOKMIN, "국민은행", "********6789", "모여타택시", null, false))
                .when(payoutAccountService).find(eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerStatus").value(nullValue()))
                .andExpect(jsonPath("$.payable").value(false));
    }

    @DisplayName("GET /api/v1/drivers/me/payout-account - 지급 계좌가 없으면 404 PAYOUT_ACCOUNT_NOT_FOUND")
    @Test
    void find_notFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(SettlementErrorCode.PAYOUT_ACCOUNT_NOT_FOUND)).when(payoutAccountService).find(eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYOUT_ACCOUNT_NOT_FOUND"));
    }
}
