package team.codingforest.moyeota.settlement.common.toss;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 토스 지급대행 요청·응답 본문의 JWE 암호화(alg=dir, enc=A256GCM)
 * 복호화한 본문이 JSON 객체면 jjwt 가 JWT 클레임(Map)으로 돌려주므로 JsonNode 로 바꿔 넘김
 */
public class TossJweCipher {
    private static final Pattern KEY_FORMAT = Pattern.compile("[0-9a-fA-F]{64}");
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter IAT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final SecretKey key;
    private final JwtParser parser;
    private final ObjectMapper objectMapper;

    public TossJweCipher(String securityKeyHex, ObjectMapper objectMapper) {
        if(securityKeyHex == null || !KEY_FORMAT.matcher(securityKeyHex).matches()) {
            throw new IllegalStateException("토스 지급대행 보안 키는 64자 hex 여야 합니다.");
        }

        this.key = new SecretKeySpec(HexFormat.of().parseHex(securityKeyHex), "AES");
        this.parser = Jwts.parser().decryptWith(key).build();
        this.objectMapper = objectMapper;
    }

    public String encrypt(String json) {
        return Jwts.builder()
                .header()
                .add("iat", ZonedDateTime.now(ZONE).format(IAT_FORMAT))
                .add("nonce", UUID.randomUUID().toString())
                .and()
                .content(json.getBytes(StandardCharsets.UTF_8))
                .encryptWith(key, Jwts.KEY.DIRECT, Jwts.ENC.A256GCM)
                .compact();
    }

    public JsonNode decrypt(String jwe) {
        Object payload = parser.parse(jwe).getPayload();

        if(payload instanceof byte[] bytes) return objectMapper.readTree(bytes);
        return objectMapper.valueToTree(payload);
    }
}