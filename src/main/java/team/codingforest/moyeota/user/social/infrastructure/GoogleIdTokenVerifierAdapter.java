package team.codingforest.moyeota.user.social.infrastructure;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.social.domain.GoogleTokenVerifier;
import team.codingforest.moyeota.user.social.domain.SocialProfile;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * 구글 라이브러리로 idToken 을 검사한다 - 서명(구글 공개키), 발급자(iss), 대상(aud), 만료(exp)
 * <p>
 * aud 가 핵심이다. 빼면 다른 앱이 받은 구글 토큰으로 우리 서비스에 로그인할 수 있다.
 * 그래서 허용할 클라이언트 ID 가 비어 있으면 검사를 건너뛰는 게 아니라 전부 거절한다(꺼짐).
 * 구글 공개키는 라이브러리가 받아와 캐시하므로 요청마다 구글을 부르지 않는다.
 */
@Slf4j
@Component
public class GoogleIdTokenVerifierAdapter implements GoogleTokenVerifier {
    private final GoogleIdTokenVerifier verifier;   // null 이면 꺼짐

    public GoogleIdTokenVerifierAdapter(@Value("${moyeota.google.client-ids:}") List<String> clientIds) {
        List<String> audiences = clientIds.stream().map(String::trim).filter(StringUtils::hasText).toList();
        if (audiences.isEmpty()) {
            log.warn("moyeota.google.client-ids 가 비어 있어 구글 로그인을 모두 거절합니다");
            this.verifier = null;
            return;
        }
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(audiences)
                .build();
    }

    @Override
    public SocialProfile verify(String idToken) {
        if (verifier == null) {
            throw new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID);
        }

        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);   // 서명·iss·aud·exp 중 하나라도 틀리면 null
        } catch (IllegalArgumentException e) {   // JWT 모양이 아님
            throw new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID);
        } catch (GeneralSecurityException | IOException e) {
            log.warn("구글 idToken 검증 실패(공개키 조회 등)", e);
            throw new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID);
        }
        if (token == null) {
            throw new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID);
        }

        GoogleIdToken.Payload payload = token.getPayload();
        // 이메일은 user_profile 필수값이다. 앱이 email 범위를 요청하지 않으면 토큰에 없다
        if (!StringUtils.hasText(payload.getEmail())) {
            log.warn("구글 idToken 에 email 이 없음 - 앱의 로그인 요청 범위(email)를 확인");
            throw new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID);
        }

        return new SocialProfile(SocialType.GOOGLE, payload.getSubject(), payload.getEmail(), (String) payload.get("name"));
    }
}
