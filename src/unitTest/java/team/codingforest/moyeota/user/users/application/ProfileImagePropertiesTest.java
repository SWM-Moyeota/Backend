package team.codingforest.moyeota.user.users.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileImagePropertiesTest {

    @Test
    void CDN_주소_끝에_슬래시가_없으면_붙인다() {
        // 환경 변수에 / 를 빼고 넣으면 https://cdn.moyeota.co.krprofile/... 처럼 도메인과 키가 붙어 버림
        ProfileImageProperties properties = new ProfileImageProperties("https://cdn.example.com", "profile/presets/default.png", List.of());

        assertThat(properties.baseUrl()).isEqualTo("https://cdn.example.com/");
    }

    @Test
    void CDN_주소_끝에_슬래시가_있으면_그대로_둔다() {
        ProfileImageProperties properties = new ProfileImageProperties("https://cdn.example.com/", "profile/presets/default.png", List.of());

        assertThat(properties.baseUrl()).isEqualTo("https://cdn.example.com/");
    }
}
