package team.codingforest.moyeota.user.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.user.domain.enums.TokenType;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtProvider {
    private final SecretKey key;
    private final JwtParser parser;
    private final Map<TokenType, Duration> validities;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.access-token-validity}") Duration accessValidity,
                       @Value("${jwt.refresh-token-validity}") Duration refreshValidity) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(key).build();
        this.validities = new EnumMap<>(Map.of(TokenType.ACCESS, accessValidity, TokenType.REFRESH, refreshValidity));
    }

    public String issue(UUID publicId, TokenType type) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(publicId.toString())
                .claim("typ", type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validities.get(type))))
                .signWith(key)
                .compact();
    }

    public UUID parse(String token, TokenType expectedType) {
        Claims claims = parser.parseSignedClaims(token).getPayload();

        if(!expectedType.name().equals(claims.get("typ"))) throw new JwtException("토큰 용도가 다릅니다.");

        return UUID.fromString(claims.getSubject());
    }

    public boolean isValid(String token, TokenType expectedType) {
        try {
            parse(token, expectedType);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}