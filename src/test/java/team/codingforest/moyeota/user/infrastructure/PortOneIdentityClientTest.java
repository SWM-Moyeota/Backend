package team.codingforest.moyeota.user.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.user.application.IdentitySettings;
import team.codingforest.moyeota.user.domain.IdentityRequest;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static team.codingforest.moyeota.user.domain.exception.IdentityErrorCode.*;

class PortOneIdentityClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.portone.io");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final IdentitySettings settings = new IdentitySettings("store", "channel", "secret", "x".repeat(32), Duration.ofMinutes(10));
    private final PortOneIdentityClient client = new PortOneIdentityClient(builder.build(), settings);
    private final IdentityRequest request = new IdentityRequest("iv-test", 1L, "store", "channel", Instant.EPOCH, Instant.EPOCH.plusSeconds(600));

    @Test
    void V2_서버인증과_상점범위로_조회하고_필요한_필드만_읽는다() {
        server.expect(requestTo("https://api.portone.io/identity-verifications/iv-test?storeId=store"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "PortOne secret"))
                .andRespond(withSuccess("""
                        {"id":"iv-test","status":"VERIFIED","version":"V2",
                         "channel":{"key":"channel","type":"LIVE","pgProvider":"KCP"},
                         "verifiedAt":"2026-09-20T00:00:00Z",
                         "verifiedCustomer":{"di":"private-di","name":"비공개","phoneNumber":"01012345678"},
                         "pgRawResponse":"비공개"}
                        """, MediaType.APPLICATION_JSON));
        var result = client.lookup(request);
        assertThat(result.di()).isEqualTo("private-di");
        assertThat(result.channelKey()).isEqualTo("channel");
        assertThat(result.toString()).doesNotContain("private-di");
        server.verify();
    }

    @Test
    void 미생성_외부인증은_미완료로_처리한다() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.lookup(request)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(NOT_VERIFIED);
    }

    @Test
    void 외부_오류본문은_노출하지_않고_재시도_오류로_변환한다() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("민감정보 원문"));
        assertThatThrownBy(() -> client.lookup(request)).isInstanceOf(BusinessException.class)
                .hasMessage(PROVIDER_UNAVAILABLE.getMessage()).hasNoCause();
    }

    @Test
    void 타임아웃과_비정상_JSON은_성공으로_처리하지_않는다() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("민감정보")));
        assertThatThrownBy(() -> client.lookup(request)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PROVIDER_UNAVAILABLE);
        server.reset();
        server.expect(anything()).andRespond(withSuccess("잘못된 JSON", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.lookup(request)).isInstanceOf(BusinessException.class);
    }
}
