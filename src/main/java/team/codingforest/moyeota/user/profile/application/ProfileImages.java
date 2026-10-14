package team.codingforest.moyeota.user.profile.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.user.profile.dto.ProfileImageResponse;

import java.util.List;

/** 프로필 이미지 키를 다룸. 제공 목록 확인과 키 → URL 변환 */
@Component
@RequiredArgsConstructor
public class ProfileImages {

    private final ProfileImageProperties properties;

    public List<String> presetKeys() {
        return properties.presets();
    }

    public boolean isPreset(String key) {
        return key != null && properties.presets().contains(key);
    }
    /**
     * 저장된 키를 응답용 전체 URL 로 바꿈. 고른 적이 없거나 기본으로 되돌렸으면 기본 이미지
     *
     * @param key DB 에 저장된 이미지 키. null 가능
     */
    public String toUrl(String key) {
        return properties.baseUrl() + (key == null ? properties.defaultKey() : key);
    }

    public List<ProfileImageResponse> presets() {
        return properties.presets().stream()
                .map(key -> new ProfileImageResponse(key, toUrl(key)))
                .toList();
    }
}