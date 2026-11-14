package team.codingforest.moyeota.user.social.dto;

public enum SocialLoginStatus {
    /** 이미 가입한 회원 - 토큰이 함께 나간다 */
    LOGIN,
    /** 처음 온 사람 - 가입 대기표가 나간다. 휴대폰 인증 뒤 POST /api/v1/auth/social/register */
    SIGNUP_REQUIRED
}
