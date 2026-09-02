package team.codingforest.moyeota.user.infrastructure;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.user.application.dto.TokenClaims;
import team.codingforest.moyeota.user.application.dto.TokenPair;
import team.codingforest.moyeota.user.domain.enums.TokenType;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final SecretKey key;
    private final JwtParser parser;
    private final Map<TokenType, Duration> validities;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.access-token-validity}") Duration accessValidity,
                       @Value("${jwt.refresh-token-validity}") Duration refreshValidity) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.parser = Jwts.parser().verifyWith(key).build();
        this.validities = new EnumMap<>(Map.of(
                TokenType.ACCESS, accessValidity,
                TokenType.REFRESH, refreshValidity));
    }

    public TokenPair issuePair(UUID publicId, UUID jti, Instant now) {
        return new TokenPair(
                build(publicId, TokenType.ACCESS, now, null),
                build(publicId, TokenType.REFRESH, now, jti),
                now.plus(validities.get(TokenType.REFRESH))
        );
    }

    private String build(UUID publicId, TokenType type, Instant now, UUID jti) {
        JwtBuilder builder = Jwts.builder()
                .subject(publicId.toString())
                .claim(TOKEN_TYPE_CLAIM, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validities.get(type))))
                .signWith(key);

        if (jti != null) {
            builder.id(jti.toString());
        }

        return builder.compact();
    }

    public UUID parseAccess(String token) {
        return parse(token, TokenType.ACCESS).publicId();
    }

    public TokenClaims parseRefresh(String token) {
        TokenClaims claims = parse(token, TokenType.REFRESH);

        if (claims.jti() == null) {
            throw new IllegalArgumentException("Invalid token");
        }

        return claims;
    }

    private TokenClaims parse(String token, TokenType expectedType) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();

            if (!expectedType.name().equals(claims.get(TOKEN_TYPE_CLAIM))) {
                throw new IllegalArgumentException("Invalid token type");
            }

            String jti = claims.getId();

            return new TokenClaims(
                    UUID.fromString(claims.getSubject()),
                    jti == null ? null : UUID.fromString(jti)
            );

        } catch (ExpiredJwtException e) {
            throw new IllegalArgumentException("Expired JWT Token");
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid JWT Token");
        }
    }
}