package team.codingforest.moyeota.user.users.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** application.yaml 이 profile-images.yaml 을 불러와 목록까지 한 설정으로 묶이는지 확인 */
@SpringBootTest(classes = ProfileImagePropertiesBindingTest.Config.class)
class ProfileImagePropertiesBindingTest {

    @Configuration
    @EnableConfigurationProperties(ProfileImageProperties.class)
    static class Config {
    }

    @Autowired
    private ProfileImageProperties properties;

    @Test
    void 별도_파일의_이미지_목록과_기본_설정의_CDN_주소가_함께_묶인다() {
        assertThat(properties.baseUrl()).isEqualTo("https://cdn.moyeota.co.kr/");
        assertThat(properties.defaultKey()).isEqualTo("profile/presets/default.png");
        assertThat(properties.presets())
                .hasSize(16)
                .startsWith("profile/presets/01.png")
                .endsWith("profile/presets/16.png")
                .doesNotContain(properties.defaultKey());
    }
}
