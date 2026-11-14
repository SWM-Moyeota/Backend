package team.codingforest.moyeota.user.social.domain;

import team.codingforest.moyeota.user.social.domain.enums.SocialType;

/**
 * 소셜 서비스가 확인해 준 사용자 정보
 *
 * @param socialId 소셜 서비스의 고정 식별자(구글은 sub)
 * @param name     소셜 계정에 적힌 이름. 실명이 아닐 수 있고 없을 수도 있다
 */
public record SocialProfile(SocialType socialType, String socialId, String email, String name) {
}
