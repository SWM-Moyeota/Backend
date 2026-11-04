package team.codingforest.moyeota.settlement.common.toss;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 토스 지급대행 API 호출. 응답은 평문이든 JWE 든 읽어 entityBody 를 돌려주고, 에러면 TossPayoutException 을 던짐
 */
@RequiredArgsConstructor
public class TossPayoutClient {
    private static final String SECURITY_MODE_HEADER = "TossPayments-api-security-mode";
    private static final String ENCRYPTION = "ENCRYPTION";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final TossJweCipher cipher;
    private final ObjectMapper objectMapper;

    public TossBalance findBalance() {
        ResponseEntity<String> response = restClient.get()
                .uri("/v2/balances")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(String.class);

        return read(response, TossBalance.class);
    }

    /**
     * 본문을 JWE 로 암호화해 POST 함. 셀러 등록·수정, 지급 요청에 씀
     *
     * @param path           API 경로
     * @param body           암호화 전 요청 본문
     * @param idempotencyKey 멱등키, 없으면 null
     * @param type           entityBody 를 바꿀 타입
     * @return 응답의 entityBody
     */
    public <T> T postEncrypted(String path, Object body, String idempotencyKey, Class<T> type) {
        String jwe = cipher.encrypt(objectMapper.writeValueAsString(body));

        ResponseEntity<String> response = restClient.post()
                .uri(path)
                .header(SECURITY_MODE_HEADER, ENCRYPTION)
                .headers(headers -> {
                    if(idempotencyKey != null) headers.set(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
                })
                .contentType(MediaType.TEXT_PLAIN)
                .body(jwe)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(String.class);

        return read(response, type);
    }

    private <T> T read(ResponseEntity<String> response, Class<T> type) {
        int status = response.getStatusCode().value();
        TossResponse body = parse(status, response.getBody());

        if(response.getStatusCode().isError() || body.error() != null) {
            throw TossPayoutException.of(status, body);
        }

        return objectMapper.treeToValue(body.entityBody(), type);
    }

    private TossResponse parse(int status, String raw) {
        if(raw == null || raw.isBlank()) throw TossPayoutException.unreadable(status, "응답 본문 없음", null);

        String trimmed = raw.trim();
        try {
            JsonNode node = trimmed.startsWith("{") ? objectMapper.readTree(trimmed) : cipher.decrypt(trimmed);
            return objectMapper.treeToValue(node, TossResponse.class);
        } catch (RuntimeException e) {
            throw TossPayoutException.unreadable(status, "응답을 읽을 수 없음: " + e.getClass().getSimpleName(), e);
        }
    }
}