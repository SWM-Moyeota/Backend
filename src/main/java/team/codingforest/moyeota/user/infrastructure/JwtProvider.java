package team.codingforest.moyeota.user.infrastructure;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.user.application.dto.TokenClaims;
import team.codingforest.moyeota.user.application.dto.TokenPair;
import team.codingforest.moyeota.user.domain.enums.TokenType;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

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
    private final Map<TokenType, Duration> validates;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.access-token-validity}") Duration accessValidity,
                       @Value("${jwt.refresh-token-validity}") Duration refreshValidity) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.parser = Jwts.parser().verifyWith(key).build();
        this.validates = new EnumMap<>(Map.of(
                TokenType.ACCESS, accessValidity,
                TokenType.REFRESH, refreshValidity));
    }

    public TokenPair issuePair(UUID publicId, UUID jti, Instant now) {
        return new TokenPair(
                build(publicId, TokenType.ACCESS, now, null),
                build(publicId, TokenType.REFRESH, now, jti),
                now.plus(validates.get(TokenType.REFRESH))
        );
    }

    private String build(UUID publicId, TokenType type, Instant now, UUID jti) {
        JwtBuilder builder = Jwts.builder()
                .subject(publicId.toString())
                .claim(TOKEN_TYPE_CLAIM, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validates.get(type))))
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
            throw new UserException(UserErrorCode.TOKEN_INVALID);
        }

        return claims;
    }

    private TokenClaims parse(String token, TokenType expectedType) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();

            if (!expectedType.name().equals(claims.get(TOKEN_TYPE_CLAIM))) {
                throw new UserException(UserErrorCode.TOKEN_INVALID);
            }

            String jti = claims.getId();

            return new TokenClaims(
                    UUID.fromString(claims.getSubject()),
                    jti == null ? null : UUID.fromString(jti)
            );

        } catch (ExpiredJwtException e) {
            throw new UserException(UserErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new UserException(UserErrorCode.TOKEN_INVALID);
        }
    }
}