package team.codingforest.moyeota.settlement.common.toss;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TossJweCipherUnitTest {
    private static final String KEY = "0123456789abcdef".repeat(4);
    private static final String OTHER_KEY = "fedcba9876543210".repeat(4);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TossJweCipher cipher = new TossJweCipher(KEY, objectMapper);

    @DisplayName("encrypt - 헤더는 alg=dir, enc=A256GCM 이고 iat 는 초 단위 ISO 8601, 암호화된 키 조각은 비어 있다")
    @Test
    void encrypt_header() {

        /*
         * test
         */
        String jwe = cipher.encrypt("{}");

        /*
         * verify
         */
        String[] parts = jwe.split("\\.", -1);
        assertThat(parts).hasSize(5);
        assertThat(parts[1]).isEmpty();

        Map<?, ?> header = header(jwe);
        assertThat(header.get("alg")).isEqualTo("dir");
        assertThat(header.get("enc")).isEqualTo("A256GCM");
        assertThat((String) header.get("iat")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}");
    }

    @DisplayName("encrypt - nonce 는 요청마다 다르다")
    @Test
    void encrypt_nonceUnique() {

        /*
         * test
         */
        Object first = header(cipher.encrypt("{}")).get("nonce");
        Object second = header(cipher.encrypt("{}")).get("nonce");

        /*
         * verify
         */
        assertThat(first).isNotNull().isNotEqualTo(second);
    }

    @DisplayName("decrypt - 암호화한 JSON 을 중첩 객체·배열까지 그대로 되돌린다")
    @Test
    void decrypt_roundTrip() {

        /*
         * settings
         */
        String json = """
                {"refSellerId":"driver-7","account":{"bankCode":"088","accountNumber":"1234"},"amounts":[1,2]}""";

        /*
         * test
         */
        JsonNode decrypted = cipher.decrypt(cipher.encrypt(json));

        /*
         * verify
         */
        assertThat(decrypted).isEqualTo(objectMapper.readTree(json));
    }

    @DisplayName("decrypt - 다른 키로 암호화한 본문은 풀지 못한다")
    @Test
    void decrypt_wrongKey() {

        /*
         * settings
         */
        String jwe = new TossJweCipher(OTHER_KEY, objectMapper).encrypt("{}");

        /*
         * test, verify
         */
        assertThatThrownBy(() -> cipher.decrypt(jwe)).isInstanceOf(JwtException.class);
    }

    @DisplayName("생성 - 보안 키가 64자 hex 가 아니면 시작할 때 실패한다")
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "zz23456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"})
    void create_invalidKey(String key) {

        /*
         * test, verify
         */
        assertThatThrownBy(() -> new TossJweCipher(key, objectMapper)).isInstanceOf(IllegalStateException.class);
    }

    private Map<?, ?> header(String jwe) {
        return objectMapper.readValue(Base64.getUrlDecoder().decode(jwe.split("\\.")[0]), Map.class);
    }
}
