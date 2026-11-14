package team.codingforest.moyeota.user.social.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.social.domain.SocialProfile;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Apple 통신 오류에 응답 본문·토큰·키를 포함하지 않는다. */
@Component
public class AppleClient {
    private static final String ISSUER = "https://appleid.apple.com";
    private final List<String> audiences;
    private final String teamId, keyId, privateKey, baseUrl;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant lastMissRefresh = Instant.EPOCH;
    private volatile Instant keysExpire = Instant.EPOCH;

    public AppleClient(@Value("${moyeota.apple.client-ids:}") List<String> audiences,
                       @Value("${moyeota.apple.team-id:}") String teamId,
                       @Value("${moyeota.apple.key-id:}") String keyId,
                       @Value("${moyeota.apple.private-key:}") String privateKey,
                       @Value("${moyeota.apple.base-url:https://appleid.apple.com}") String baseUrl) {
        this.audiences = audiences.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.teamId = teamId; this.keyId = keyId; this.privateKey = privateKey; this.baseUrl = baseUrl;
    }

    public Verified exchange(String identityToken, String code, String nonce, String name) {
        Claims identity = verify(identityToken, true);
        if (!Objects.equals(nonce, identity.get("nonce", String.class))) throw invalid();
        String audience = identity.getAudience().iterator().next();
        JsonNode response = post("/auth/token", Map.of("client_id", audience, "client_secret", clientSecret(audience),
                "code", code, "grant_type", "authorization_code"));
        Claims exchanged = verify(response.path("id_token").asText(), true);
        if (!identity.getSubject().equals(exchanged.getSubject()) || !identity.getAudience().equals(exchanged.getAudience())
                || !Objects.equals(nonce, exchanged.get("nonce", String.class))) throw invalid();
        String refresh = response.path("refresh_token").asText();
        if (refresh.isBlank()) throw invalid();
        String email = identity.get("email", String.class);
        if (email != null && !"true".equals(String.valueOf(identity.get("email_verified")))) throw invalid();
        return new Verified(new SocialProfile(SocialType.APPLE, identity.getSubject(), email, name), audience, refresh, identity.getIssuedAt().toInstant());
    }

    public String subject(String identity) { return verify(identity, true).getSubject(); }

    public Claims verifyNotification(String payload) { return verify(payload, false); }

    public void revoke(String audience, String refreshToken) {
        post("/auth/revoke", Map.of("client_id", audience, "client_secret", clientSecret(audience),
                "token", refreshToken, "token_type_hint", "refresh_token"));
    }

    private Claims verify(String token, boolean requireExpiration) {
        try {
            if (audiences.isEmpty() || token == null || token.length() > 20000) throw invalid();
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw invalid();
            JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!"RS256".equals(header.path("alg").asText())) throw invalid();
            PublicKey key = key(header.path("kid").asText());
            if (key == null) throw invalid();
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build().parseSignedClaims(token).getPayload();
            Instant now = Instant.now();
            if (claims.getAudience() == null || claims.getAudience().size() != 1 || !audiences.containsAll(claims.getAudience())) throw invalid();
            if (requireExpiration && (claims.getExpiration() == null || claims.getSubject() == null || claims.getSubject().isBlank())) throw invalid();
            if (claims.getIssuedAt() == null || claims.getIssuedAt().toInstant().isAfter(now.plusSeconds(30))) throw invalid();
            if (!requireExpiration && claims.getIssuedAt().toInstant().isBefore(now.minus(Duration.ofDays(7)))) throw invalid();
            return claims;
        } catch (UserException e) { throw e; }
        catch (Exception e) { throw invalid(); }
    }

    private synchronized PublicKey key(String kid) {
        PublicKey found = publicKeys().get(kid);
        if (found == null && Instant.now().isAfter(lastMissRefresh.plusSeconds(30))) {
            lastMissRefresh = Instant.now(); keysExpire = Instant.EPOCH;
            found = publicKeys().get(kid);
        }
        return found;
    }

    private synchronized Map<String, PublicKey> publicKeys() {
        if (Instant.now().isBefore(keysExpire)) return keys;
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/auth/keys"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw unavailable();
            Map<String, PublicKey> loaded = new HashMap<>();
            for (JsonNode jwk : json.readTree(response.body()).path("keys")) {
                if (!"RSA".equals(jwk.path("kty").asText()) || !"RS256".equals(jwk.path("alg").asText())) continue;
                loaded.put(jwk.path("kid").asText(), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText())),
                        new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText())))));
            }
            keys = Map.copyOf(loaded); keysExpire = Instant.now().plusSeconds(300); return keys;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception e) { throw unavailable(); }
    }

    private String clientSecret(String audience) {
        try {
            if (teamId.isBlank() || keyId.isBlank() || privateKey.isBlank()) throw unavailable();
            String pem = privateKey.replace("\\n", "\n").replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            Instant now = Instant.now();
            return Jwts.builder().header().keyId(keyId).and().issuer(teamId).subject(audience)
                    .audience().add(ISSUER).and().issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                    .signWith(key, Jwts.SIG.ES256).compact();
        } catch (Exception e) { throw unavailable(); }
    }

    private JsonNode post(String path, Map<String, String> fields) {
        try {
            String body = fields.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500 || response.statusCode() == 429) throw unavailable();
            if (response.statusCode() != 200) throw invalid();
            return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
        } catch (UserException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception e) { throw unavailable(); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static UserException invalid() { return new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID); }
    private static UserException unavailable() { return new UserException(UserErrorCode.SOCIAL_UNAVAILABLE); }
    // record 기본 toString으로 공급자 토큰을 노출하지 않는다.
    public record Verified(SocialProfile profile, String clientId, String refreshToken, Instant authenticatedAt) {
        public Verified(SocialProfile profile, String clientId, String refreshToken) { this(profile, clientId, refreshToken, Instant.now()); }
        @Override public String toString() { return "Apple 인증 결과(민감정보 생략)"; }
    }
}
