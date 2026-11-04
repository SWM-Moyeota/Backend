package team.codingforest.moyeota.settlement.ledger.infrastructure.toss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 토스 테스트 환경에 실제로 붙어 키와 클라이언트를 확인함
 * 키는 환경변수, 없으면 application-local.yaml(git 미추적)에서 읽고 둘 다 없으면 건너뛰어 CI 에서는 돌지 않음
 */
class TossPayoutBalanceCheckTest {
    private static final Properties LOCAL = localProperties();

    private TossPayoutClient client;

    @BeforeEach
    void setUp() {
        String secretKey = value("TOSS_PAYOUT_SECRET_KEY", "moyeota.settlement.toss.secret-key");
        assumeTrue(secretKey != null && !secretKey.isBlank(), "토스 키가 없어 건너뜀");
        assumeTrue(secretKey.startsWith("test_"), "라이브 키로는 돌리지 않음");

        TossPayoutProperties properties = new TossPayoutProperties("https://api.tosspayments.com", secretKey,
                value("TOSS_PAYOUT_SECURITY_KEY", "moyeota.settlement.toss.security-key"), Duration.ofSeconds(3), Duration.ofSeconds(10));
        ObjectMapper objectMapper = new ObjectMapper();
        TossPayoutConfig config = new TossPayoutConfig();
        client = config.tossPayoutClient(properties, config.tossJweCipher(properties, objectMapper), objectMapper);
    }

    @DisplayName("잔액 조회 - 테스트 키로 지급 가능 잔액을 받는다")
    @Test
    void findBalance() {

        /*
         * test
         */
        TossBalance balance = client.findBalance();

        /*
         * verify
         */
        System.out.println("토스 잔액 응답: " + balance);
        assertThat(balance.availableAmount()).isNotNull();
        assertThat(balance.availableAmount().currency()).isEqualTo("KRW");
    }

    @DisplayName("암호화 요청 - 빈 셀러 등록을 보내면 토스가 JWE 를 풀어 검증하고 4xx 에러를 돌려준다 (셀러는 만들어지지 않음)")
    @Test
    void postEncrypted_emptySeller() {

        /*
         * test
         */
        TossPayoutException thrown = catchThrowableOfType(TossPayoutException.class,
                () -> client.postEncrypted("/v2/sellers", Map.of(), null, Map.class));

        /*
         * verify
         */
        assertThat(thrown).as("빈 본문인데 셀러가 만들어짐").isNotNull();
        System.out.println("토스 에러 응답: " + thrown.getMessage());
        assertThat(thrown.getHttpStatus()).isBetween(400, 499);
        assertThat(thrown.getCode()).as("에러 본문을 읽지 못함").isNotNull();
        assertThat(thrown.getCode()).as("토스가 요청 본문을 복호화하지 못함 - 보안 키·JWE 형식 확인").isNotEqualTo("INVALID_ENCRYPTION");
    }

    private static String value(String env, String property) {
        String fromEnv = System.getenv(env);
        if(fromEnv != null && !fromEnv.isBlank()) return fromEnv;
        return LOCAL.getProperty(property);
    }

    private static Properties localProperties() {
        ClassPathResource local = new ClassPathResource("application-local.yaml");
        if(!local.exists()) return new Properties();

        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(local);
        return yaml.getObject();
    }
}
