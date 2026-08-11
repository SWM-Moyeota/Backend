package team.codingforest.moyeota.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
구글 로그인이 끝난 직후 프론트에게 건네줄 "일회용 교환 코드"를 관리한다.

왜 필요한가.
구글 로그인의 마지막 단계는 브라우저 주소창 이동(302)이라서, JWT를 응답 바디로 줄 수가 없다.
브라우저는 302의 바디를 버리고 Location만 따라가기 때문이다.
그래서 리다이렉트를 건너 살아남는 통로는 URL 아니면 쿠키뿐인데,
URL에 JWT를 그대로 실으면 주소창/브라우저 히스토리에 24시간짜리 refresh 토큰이 남는다.

대신 30초만 사는 무의미한 난수를 URL에 실어 보내고,
프론트가 그걸 POST /api/v1/auth/exchange 로 되돌려주면 그때 진짜 토큰을 200 JSON으로 준다.
URL로 새어 나가는 것은 이미 만료됐거나 이미 사용된 코드가 된다.

왜 DB가 아니라 메모리인가.
수명이 30초라 서버가 재시작되면 어차피 다 무효다. 로그인 한 번마다 insert/delete 하는 것도 아깝다.
다만 서버를 여러 대로 늘리면(로드밸런서 뒤) 1번 서버가 발급한 코드를 2번 서버가 모른다.
그때는 이 클래스의 내부만 Redis 같은 공용 저장소로 바꾸면 된다(바깥 API는 그대로).
*/
@Service
public class AuthCodeService {

    //코드 수명. 리다이렉트 직후 프론트가 바로 교환하므로 짧을수록 좋다.
    private static final long CODE_EXP = 30 * 1000L;   //30초

    //예측 불가능해야 하므로 Random이 아니라 SecureRandom을 쓴다.
    private static final SecureRandom RANDOM = new SecureRandom();

    //코드 -> 발급 대상. ConcurrentHashMap이라 remove가 원자적이고, 그것이 곧 "1회용" 보장이 된다.
    private final Map<String, Payload> store = new ConcurrentHashMap<>();

    //코드에 매달아 둘 정보. 토큰 자체가 아니라 "누구에게 발급할 것인가"만 들고 있는다.
    //그래야 교환되지 않은 로그인 때문에 refresh 토큰이 DB에 쌓이지 않는다.
    private record Payload(String username, String role, long expiresAt) {

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    //구글 로그인 성공 직후 호출. 코드만 만들어 돌려준다(토큰은 아직 만들지 않는다).
    public String issue(String username, String role) {

        //만료된 코드가 계속 쌓이지 않게 발급할 때마다 한 번 쓸어낸다.
        //별도 스케줄러를 두기엔 양이 적고, 로그인 빈도만큼만 돌면 충분하다.
        store.values().removeIf(Payload::isExpired);

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        //URL에 실려야 하므로 +, / 가 없는 URL-safe 인코딩을 쓴다. 패딩(=)도 뺀다.
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        store.put(code, new Payload(username, role, System.currentTimeMillis() + CODE_EXP));

        return code;
    }

    //코드를 토큰 발급 정보로 바꾼다. 성공하든 만료됐든 그 코드는 이 순간 사라진다.
    public UsernameAndRole consume(String code) {

        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code null");
        }

        //조회가 아니라 제거로 꺼낸다. 같은 코드로 두 번 요청이 들어와도 한 쪽만 값을 받는다.
        Payload payload = store.remove(code);

        //없는 코드인지 만료된 코드인지 구분해서 알려주지 않는다(공격자에게 정보를 주지 않기 위해).
        if (payload == null || payload.isExpired()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or expired code");
        }

        return new UsernameAndRole(payload.username(), payload.role());
    }

    public record UsernameAndRole(String username, String role) {
    }
}
