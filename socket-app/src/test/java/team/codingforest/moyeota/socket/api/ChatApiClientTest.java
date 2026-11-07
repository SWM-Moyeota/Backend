package team.codingforest.moyeota.socket.api;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.socket.auth.ChatPrincipal;
import team.codingforest.moyeota.socket.common.SocketErrorCode;
import team.codingforest.moyeota.socket.common.SocketException;

import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class ChatApiClientTest {

    private static final String BASE_URL = "http://api";
    private static final String PRINCIPAL_URL = BASE_URL + "/internal/chat/principal";
    private static final String PARTICIPANT_URL = BASE_URL + "/internal/chat/chat-rooms/10/users/7";
    private static final String AUTHORIZATION = "Bearer test-token";
    private static final UUID PUBLIC_ID = UUID.fromString("3f7a1c2e-8b4d-4c1a-9f2e-1234567890ab");

    private MockRestServiceServer server;
    private ChatApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ChatApiClient(builder.build());
    }

    @Test
    void 받은_토큰을_그대로_넘기고_사용자를_받는다() {
        server.expect(requestTo(PRINCIPAL_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andRespond(withSuccess("{\"userId\":7,\"publicId\":\"" + PUBLIC_ID + "\"}", MediaType.APPLICATION_JSON));

        ChatPrincipal principal = client.principal(AUTHORIZATION);

        assertThat(principal).isEqualTo(new ChatPrincipal(7L, PUBLIC_ID));
        assertThat(principal.getName()).isEqualTo("7");
    }

    @Test
    void 만료_코드면_TOKEN_EXPIRED_로_거절한다() {
        server.expect(requestTo(PRINCIPAL_URL))
                .andRespond(unauthorized("USER002"));

        assertError(() -> client.principal(AUTHORIZATION), SocketErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 만료가_아닌_401_이면_TOKEN_INVALID_로_거절한다() {
        server.expect(requestTo(PRINCIPAL_URL))
                .andRespond(unauthorized("USER001"));

        assertError(() -> client.principal(AUTHORIZATION), SocketErrorCode.TOKEN_INVALID);
    }

    @Test
    void API_가_응답하지_않으면_SERVER_UNAVAILABLE_로_거절한다() {
        server.expect(requestTo(PRINCIPAL_URL))
                .andRespond(withException(new SocketTimeoutException()));

        assertError(() -> client.principal(AUTHORIZATION), SocketErrorCode.SERVER_UNAVAILABLE);
    }

    @Test
    void API_가_5xx_면_SERVER_UNAVAILABLE_로_거절한다() {
        server.expect(requestTo(PRINCIPAL_URL))
                .andRespond(withServerError());

        assertError(() -> client.principal(AUTHORIZATION), SocketErrorCode.SERVER_UNAVAILABLE);
    }

    @Test
    void 방에_남아_있으면_통과한다() {
        server.expect(requestTo(PARTICIPANT_URL))
                .andRespond(withNoContent());

        assertThatCode(() -> client.validateParticipant(10L, 7L)).doesNotThrowAnyException();
    }

    @Test
    void 방에_없으면_NOT_PARTICIPANT_로_거절한다() {
        server.expect(requestTo(PARTICIPANT_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertError(() -> client.validateParticipant(10L, 7L), SocketErrorCode.NOT_PARTICIPANT);
    }

    private static ResponseCreator unauthorized(String code) {
        return withUnauthorizedRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"" + code + "\",\"message\":\"x\"}");
    }

    private static void assertError(ThrowingCallable call, SocketErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(SocketException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
