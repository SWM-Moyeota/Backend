package team.codingforest.moyeota.user.social.infrastructure;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

import java.util.Base64;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *  진짜 구글 토큰은 테스트에서 만들 수 없어 거절 경로만 본다. 모두 구글을 부르기 전에 끝나는 경우다
 */
class GoogleIdTokenVerifierAdapterTest {
    private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";

    @Test
    void 클라이언트_ID_가_비어_있으면_검사를_건너뛰지_않고_모두_거절한다() {
        GoogleIdTokenVerifierAdapter verifier = new GoogleIdTokenVerifierAdapter(List.of());

        assertInvalid(verifier, "아무-토큰");
    }

    @Test
    void 공백뿐인_클라이언트_ID_도_비어_있는_것으로_본다() {
        GoogleIdTokenVerifierAdapter verifier = new GoogleIdTokenVerifierAdapter(List.of("", "  "));

        assertInvalid(verifier, "아무-토큰");
    }

    @Test
    void JWT_모양이_아닌_문자열은_거절한다() {
        GoogleIdTokenVerifierAdapter verifier = new GoogleIdTokenVerifierAdapter(List.of(CLIENT_ID));

        assertInvalid(verifier, "not-a-jwt");
    }

    @Test
    void 다른_앱용으로_발급된_토큰은_서명을_보기_전에_거절한다() {
        GoogleIdTokenVerifierAdapter verifier = new GoogleIdTokenVerifierAdapter(List.of(CLIENT_ID));
        long exp = System.currentTimeMillis() / 1000 + 600;
        String forOtherApp = jwt("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"k\"}",
                "{\"iss\":\"https://accounts.google.com\",\"aud\":\"other-app.apps.googleusercontent.com\","
                        + "\"sub\":\"1\",\"email\":\"a@b.com\",\"iat\":" + (exp - 1200) + ",\"exp\":" + exp + "}");

        assertInvalid(verifier, forOtherApp);
    }

    private static String jwt(String header, String payload) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(header.getBytes(UTF_8)) + "." + encoder.encodeToString(payload.getBytes(UTF_8))
                + "." + encoder.encodeToString("signature".getBytes(UTF_8));
    }

    private static void assertInvalid(GoogleIdTokenVerifierAdapter verifier, String idToken) {
        assertThatThrownBy(() -> verifier.verify(idToken))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.SOCIAL_TOKEN_INVALID);
    }
}
