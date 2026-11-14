package team.codingforest.moyeota._config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yaml 이 두 기준을 빈 환경변수(<code>${MIN_VERSION_CODE:}</code>)로 받는다.
 * 빈 문자열이 숫자로 변환되다 터지지 않고 null 로 묶여야 평시에 아무 설정도 하지 않은 상태로 둘 수 있다.
 */
@SpringBootTest(classes = AppPropertiesBindingTest.Config.class)
class AppPropertiesBindingTest {

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    @Autowired
    private AppProperties properties;

    @Test
    void 환경변수를_넣지_않으면_두_기준이_모두_없다() {
        assertThat(properties.minVersionCode()).isNull();
        assertThat(properties.recommendedVersionCode()).isNull();
    }
}
