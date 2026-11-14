package team.codingforest.moyeota.user.social.domain;

/** 앱이 구글 SDK 로 받은 idToken 을 검사하는 포트. 구현(구글 라이브러리)은 infrastructure 에. SmsSender 와 같은 패턴 */
public interface GoogleTokenVerifier {
    /** 구글이 우리 앱용으로 발급한 유효한 토큰이면 사용자 정보를 돌려준다. 아니면 SOCIAL_TOKEN_INVALID(USER401) */
    SocialProfile verify(String idToken);
}
