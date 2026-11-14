package team.codingforest.moyeota.user.social.apple;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.user.common.exception.*;
import team.codingforest.moyeota.user.social.application.*;
import team.codingforest.moyeota.user.social.infrastructure.*;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;
import team.codingforest.moyeota.user.social.dto.SocialLoginResponse;
import team.codingforest.moyeota.user.users.domain.Users;
import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppleAuthService {
    private final AppleClient client;
    private final AppleGrantLocks locks;
    private final SocialProofStore proofs;
    private final AppleCredentialStore credentials;
    private final SocialAuthService auth;
    private final LoginMethodsService methods;
    private final SocialUserRepository social;
    private final Users users;
    private final TransactionTemplate transactions;

    public Challenge challenge(Long userId) {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Challenge(proofs.issue(userId == null ? "APPLE_LOGIN" : "APPLE_LINK", userId, nonce), nonce, 300);
    }
    public SocialLoginResponse login(String identity, String code, String challenge, String name) {
        String nonce = proofs.consume(challenge, "APPLE_LOGIN", null, UserErrorCode.SOCIAL_CHALLENGE_INVALID);
        Staged staged = exchange(identity, code, nonce, name);
        AppleClient.Verified verified = staged.verified();
        UUID credential = staged.id();
        return transactions.execute(status -> {
            var existing = social.findBySocialTypeAndSocialId(SocialType.APPLE, verified.profile().socialId());
            if (existing.isPresent()) {
                Long id = existing.get().getUserId();
                if (users.findByIdForUpdate(id).isDeleted()) throw new UserException(UserErrorCode.USER_NOT_FOUND);
                // 사용자 잠금을 얻기 전에 조회한 엔티티를 다시 사용하지 않는다.
                var current = social.findBySocialTypeAndSocialId(SocialType.APPLE, verified.profile().socialId())
                        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
                current.activate(); social.saveAndFlush(current);
                credentials.attach(credential, id);
            }
            SocialLoginResponse response = auth.loginVerified(verified.profile());
            if (response.signupToken() != null) credentials.bindSignup(credential, response.signupToken());
            return response;
        });
    }
    public void link(Long userId, String identity, String code, String challenge, String verification) {
        String nonce = proofs.consume(challenge, "APPLE_LINK", userId, UserErrorCode.SOCIAL_CHALLENGE_INVALID);
        Staged staged = exchange(identity, code, nonce, null);
        AppleClient.Verified verified = staged.verified();
        UUID credential = staged.id();
        methods.link(userId, verified.profile(), verification, credential);
    }
    private Staged exchange(String identity, String code, String nonce, String name) {
        String subject = client.subject(identity);
        return transactions.execute(status -> {
            locks.lock(subject);
            var verified = client.exchange(identity,code,nonce,name);
            return new Staged(verified,credentials.stage(verified));
        });
    }
    private record Staged(AppleClient.Verified verified, UUID id) {}
    public record Challenge(String challengeToken, String nonce, long expiresInSeconds) {}
}
