package team.codingforest.moyeota.user.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
public class FieldHasher {
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public FieldHasher(@Value("${moyeota.crypto.hash-key}") String secret) {
        Assert.hasText(secret, "moyeota.crypto.hash-key 가 비어 있습니다");
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);   // Mac 은 스레드 안전하지 않아 호출마다 만든다
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("검색용 해시 계산 실패", e);
        }
    }
}
