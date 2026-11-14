package team.codingforest.moyeota.user.social.apple;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import team.codingforest.moyeota.user.common.exception.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 공급자 HTTP 서버와 실제 RSA/EC 서명으로 검증한다. 실제 계정·키·네트워크는 사용하지 않는다. */
class AppleClientTest {
    HttpServer server;
    KeyPair rsa;
    AppleClient client;
    String exchangeToken;
    int tokenStatus = 200, revokeStatus = 200, tokenRequests;
    String lastTokenForm, lastRevokeForm;
    @BeforeEach void 준비() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); rsa = generator.generateKeyPair();
        var ec = KeyPairGenerator.getInstance("EC"); ec.initialize(256);
        String pem = Base64.getEncoder().encodeToString(ec.generateKeyPair().getPrivate().getEncoded());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        RSAPublicKey key = (RSAPublicKey)rsa.getPublic();
        server.createContext("/auth/keys", request -> {
            String body = "{\"keys\":[{\"kid\":\"test\",\"alg\":\"RS256\",\"kty\":\"RSA\",\"n\":\"" + number(key.getModulus()) + "\",\"e\":\"" + number(key.getPublicExponent()) + "\"}]}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8); request.sendResponseHeaders(200,bytes.length); request.getResponseBody().write(bytes); request.close();
        });
        server.createContext("/auth/token", request -> {
            tokenRequests++; lastTokenForm = new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String body = tokenStatus == 200 ? "{\"id_token\":\""+exchangeToken+"\",\"refresh_token\":\"provider-refresh\"}" : "{\"error\":\"invalid_grant\"}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8); request.sendResponseHeaders(tokenStatus,bytes.length); request.getResponseBody().write(bytes); request.close();
        });
        server.createContext("/auth/revoke", request -> {
            lastRevokeForm = new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            request.sendResponseHeaders(revokeStatus,-1); request.close();
        });
        server.start();
        client = new AppleClient(List.of("app.test"),"team","ec-key",pem,"http://127.0.0.1:"+server.getAddress().getPort());
        exchangeToken = token("subject","nonce","app.test",Instant.now().plusSeconds(300),rsa.getPrivate());
    }
    @AfterEach void 종료() { server.stop(0); }
    @Test void 정상_서명과_코드를_검증하고_철회한다() {
        var result = client.exchange(exchangeToken,"code+value","nonce","홍길동");
        assertThat(result.profile().socialId()).isEqualTo("subject");
        assertThat(result.refreshToken()).isEqualTo("provider-refresh");
        assertThat(result.toString()).doesNotContain("provider-refresh");
        assertThat(lastTokenForm).contains("code=code%2Bvalue","grant_type=authorization_code","client_secret=");
        client.revoke(result.clientId(),result.refreshToken());
        assertThat(lastRevokeForm).contains("token_type_hint=refresh_token","token=provider-refresh");
    }
    @Test void nonce_불일치는_코드를_교환하지_않는다() {
        invalid(() -> client.exchange(exchangeToken,"code","wrong",null)); assertThat(tokenRequests).isZero();
    }
    @Test void 다른_앱과_만료된_토큰은_거절한다() {
        invalid(() -> client.exchange(token("subject","nonce","other",Instant.now().plusSeconds(60),rsa.getPrivate()),"code","nonce",null));
        invalid(() -> client.exchange(token("subject","nonce","app.test",Instant.now().minusSeconds(60),rsa.getPrivate()),"code","nonce",null));
        assertThat(tokenRequests).isZero();
    }
    @Test void 위조_서명은_거절한다() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        String forged=token("subject","nonce","app.test",Instant.now().plusSeconds(60),generator.generateKeyPair().getPrivate());
        invalid(() -> client.exchange(forged,"code","nonce",null));
    }
    @Test void 코드_교환_결과의_subject가_다르면_거절한다() {
        String identity=exchangeToken; exchangeToken=token("other","nonce","app.test",Instant.now().plusSeconds(60),rsa.getPrivate());
        invalid(() -> client.exchange(identity,"code","nonce",null));
    }
    @Test void 코드_재사용과_공급자_장애를_구분한다() {
        tokenStatus=400; invalid(() -> client.exchange(exchangeToken,"code","nonce",null));
        tokenStatus=503;
        assertThatThrownBy(() -> client.exchange(exchangeToken,"code","nonce",null)).isInstanceOf(UserException.class).extracting("errorCode").isEqualTo(UserErrorCode.SOCIAL_UNAVAILABLE);
    }
    @Test void 철회_실패를_호출자에게_전달한다() {
        revokeStatus=503;
        assertThatThrownBy(() -> client.revoke("app.test","refresh")).isInstanceOf(UserException.class).extracting("errorCode").isEqualTo(UserErrorCode.SOCIAL_UNAVAILABLE);
    }
    @Test void 설정이_없으면_검증을_건너뛰지_않는다() {
        var disabled=new AppleClient(List.of(),"","","","http://127.0.0.1:"+server.getAddress().getPort());
        invalid(() -> disabled.exchange(exchangeToken,"code","nonce",null));
    }
    @Test void 알림의_서명과_발급시간과_audience를_검증한다() {
        Instant now=Instant.now();
        String valid=Jwts.builder().header().keyId("test").and().issuer("https://appleid.apple.com")
                .audience().add("app.test").and().issuedAt(Date.from(now)).id("event")
                .claim("events",Map.of("type","account-deleted","sub","subject","event_time",now.getEpochSecond()))
                .signWith(rsa.getPrivate(),Jwts.SIG.RS256).compact();
        assertThat(client.verifyNotification(valid).getId()).isEqualTo("event");
        String old=Jwts.builder().header().keyId("test").and().issuer("https://appleid.apple.com")
                .audience().add("app.test").and().issuedAt(Date.from(now.minusSeconds(8*86400)))
                .signWith(rsa.getPrivate(),Jwts.SIG.RS256).compact();
        invalid(() -> client.verifyNotification(old));
        String other=Jwts.builder().header().keyId("test").and().issuer("https://attacker.example")
                .audience().add("app.test").and().issuedAt(Date.from(now)).signWith(rsa.getPrivate(),Jwts.SIG.RS256).compact();
        invalid(() -> client.verifyNotification(other));
    }

    private String token(String subject,String nonce,String audience,Instant expiry,PrivateKey key) {
        return Jwts.builder().header().keyId("test").and().issuer("https://appleid.apple.com").subject(subject)
                .audience().add(audience).and().issuedAt(Date.from(Instant.now())).expiration(Date.from(expiry))
                .claim("nonce",nonce).claim("email","private@privaterelay.appleid.com").claim("email_verified","true").signWith(key,Jwts.SIG.RS256).compact();
    }
    private static String number(java.math.BigInteger n) { byte[] b=n.toByteArray(); return Base64.getUrlEncoder().withoutPadding().encodeToString(b[0]==0?Arrays.copyOfRange(b,1,b.length):b); }
    private static void invalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) { assertThatThrownBy(action).isInstanceOf(UserException.class).extracting("errorCode").isEqualTo(UserErrorCode.SOCIAL_TOKEN_INVALID); }
}
