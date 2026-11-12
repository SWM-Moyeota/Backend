package team.codingforest.moyeota.user.passwordreset.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.user.common.crypto.FieldHasher;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PasswordResetSecrets {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final FieldHasher fieldHasher;

    public String newOpaqueValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String newCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    public String codeDigest(String requestId, String code) {
        return fieldHasher.hash("PASSWORD_RESET:CODE:" + requestId + ":" + code);
    }

    public String tokenDigest(String token) {
        return fieldHasher.hash("PASSWORD_RESET:GRANT:" + token);
    }

    public String phoneDigest(String normalizedPhone) {
        // 기존 프로필 번호 해시와 동일한 규칙
        return fieldHasher.hash(normalizedPhone);
    }
}
