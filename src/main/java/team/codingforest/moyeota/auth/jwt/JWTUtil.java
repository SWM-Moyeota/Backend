package team.codingforest.moyeota.auth.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JWTUtil {

    private SecretKey secretKey;

    public JWTUtil(@Value("${spring.jwt.secret}")String secret) {
        secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), Jwts.SIG.HS256.key().build().getAlgorithm());
    }

    //토큰의 주인(publicId)을 꺼낸다.
    //표준 클레임 sub에 담기므로 커스텀 클레임처럼 이름으로 찾지 않고 전용 메서드를 쓴다.
    public String getUsername(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().getSubject();
    }

    public String getRole(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("role", String.class);
    }

    //추가: access인지 refresh인지 구분
    public String getCategory(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("category", String.class);
    }

    /*
    이 토큰을 우리 서버가 발급한 것으로 볼 수 있는지 확인한다. 만료 여부는 보지 않는다.

    [왜 따로 필요한가]
    아래 getUsername/getRole/getCategory/isExpired는 전부 파싱을 하는데,
    서명이 맞지 않거나(SignatureException) JWT 모양이 아니면(MalformedJwtException)
    그 자리에서 예외를 던진다. isExpired는 만료 예외만 잡으므로 나머지는 그대로 튀어나가고,
    호출한 필터·컨트롤러를 뚫고 나가 500이 된다. 위조 토큰에 500을 주면
    "처리되지 않는 경로가 있다"는 신호를 주는 셈이라 401로 막아야 한다.
    그래서 다른 검사보다 먼저 이 메서드로 걸러낸다.

    만료 예외를 true로 처리하는 이유:
    JJWT는 서명을 먼저 확인한 뒤에 만료를 따진다. 즉 ExpiredJwtException이 나왔다는 것은
    서명은 이미 통과했다는 뜻이므로 "우리가 발급한 토큰이 맞다"가 된다.
    만료됐다는 사실은 isExpired가 따로 알려준다.

    IllegalArgumentException까지 잡는 이유는 null이나 빈 문자열이 들어온 경우다.
    */
    public boolean isSignatureValid(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    //만료된 토큰은 ExpiredJwtException을 던지므로 잡아서 true 반환.
    //서명이 깨진 토큰은 여기서 걸러지지 않는다. 부르기 전에 isSignatureValid로 확인할 것.
    public Boolean isExpired(String token) {
        try {
            return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /*
    수정: category 파라미터 추가

    username 자리에는 로그인 아이디가 아니라 publicId(UUID 문자열)가 들어온다.
    이 값은 표준 클레임 sub("이 토큰이 누구의 것인가")에 담는다.
    커스텀 이름 대신 표준을 쓰면 라이브러리의 getSubject()를 그대로 쓸 수 있고,
    나중에 다른 인증 서버와 붙일 때도 규격이 맞는다.
    sub는 스펙상 문자열이어야 하므로 숫자를 넣을 일이 생기면 String으로 바꿔서 넣어야 한다.
    */
    public String createJwt(String category, String username, String role, Long expiredMs) {

        return Jwts.builder()
                .claim("category", category)
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredMs))
                .signWith(secretKey)
                .compact();
    }
}
