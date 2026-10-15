package team.codingforest.moyeota.user.users.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 프로필 이미지 설정. DB 에는 키만 저장하고 응답에서 baseUrl 을 붙여 전체 URL 로 만듦
 *
 * @param baseUrl    CDN 주소. 끝에 / 포함
 * @param defaultKey 이미지를 고른 적 없거나 기본으로 되돌린 사용자에게 보여줄 키
 * @param presets    사용자가 고를 수 있는 제공 이미지 키 목록. 앱에 이 순서로 보여줌
 */
@ConfigurationProperties(prefix = "moyeota.profile-image")
public record ProfileImageProperties(String baseUrl, String defaultKey, List<String> presets) {
}