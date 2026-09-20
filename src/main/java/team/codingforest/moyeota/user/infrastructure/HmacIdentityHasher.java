package team.codingforest.moyeota.user.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.user.application.IdentitySettings;
import team.codingforest.moyeota.user.domain.IdentityHasher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class HmacIdentityHasher implements IdentityHasher {
    private final IdentitySettings settings;

    @Override
    public String hash(String di) {
        settings.requireConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.hashKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(di.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("본인인증 식별 정보 처리 실패");
        }
    }
}
