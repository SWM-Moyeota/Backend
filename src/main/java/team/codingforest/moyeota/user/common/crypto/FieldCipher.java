package team.codingforest.moyeota.user.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class FieldCipher {
    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public FieldCipher(@Value("${moyeota.crypto.field-key}") String base64Key) {
        Assert.hasText(base64Key, "moyeota.crypto.field-key 가 비어 있습니다");
        byte[] bytes = Base64.getDecoder().decode(base64Key);
        Assert.isTrue(bytes.length == KEY_BYTES, "moyeota.crypto.field-key 는 32바이트(Base64)여야 합니다");
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);   // Cipher 는 스레드 안전하지 않아 호출마다 만든다
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("개인정보 암호화 실패", e);
        }
    }

    public String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            return stored;   // 암호화 전에 저장된 평문 행 - 로컬 DB, 백필 전 데이터
        }
        byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("개인정보 복호화 실패 - 키가 다르거나 값이 변조됨", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value.startsWith(PREFIX);
    }
}
