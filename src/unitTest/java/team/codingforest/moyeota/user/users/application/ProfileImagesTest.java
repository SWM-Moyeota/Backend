package team.codingforest.moyeota.user.users.application;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.users.dto.ProfileImageResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileImagesTest {

    private final ProfileImages profileImages = new ProfileImages(new ProfileImageProperties(
            "https://cdn.example.com/",
            "profile/presets/default.png",
            List.of("profile/presets/01.png", "profile/presets/02.png")));

    @Test
    void 제공_이미지를_설정_순서대로_키와_URL_로_돌려준다() {
        assertThat(profileImages.presets()).containsExactly(
                new ProfileImageResponse("profile/presets/01.png", "https://cdn.example.com/profile/presets/01.png"),
                new ProfileImageResponse("profile/presets/02.png", "https://cdn.example.com/profile/presets/02.png"));
    }

    @Test
    void 키가_있으면_그_키의_URL_을_돌려준다() {
        assertThat(profileImages.toUrl("profile/presets/02.png"))
                .isEqualTo("https://cdn.example.com/profile/presets/02.png");
    }

    @Test
    void 키가_없으면_기본_이미지_URL_을_돌려준다() {
        assertThat(profileImages.toUrl(null)).isEqualTo("https://cdn.example.com/profile/presets/default.png");
    }

    @Test
    void 목록에_있는_키만_제공_이미지다() {
        assertThat(profileImages.isPreset("profile/presets/01.png")).isTrue();
        assertThat(profileImages.isPreset("profile/presets/default.png")).isFalse();
        assertThat(profileImages.isPreset("https://evil.example.com/x.png")).isFalse();
    }

    @Test
    void 키가_null_이면_제공_이미지가_아니다() {
        assertThat(profileImages.isPreset(null)).isFalse();
    }
}
