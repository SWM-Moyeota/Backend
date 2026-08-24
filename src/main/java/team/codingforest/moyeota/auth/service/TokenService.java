package team.codingforest.moyeota.auth.service;

//토큰 발급 규칙(만료시간 상수, refresh의 DB 저장)을 한 곳에 모아둔 자리.
//웹 로그인(CustomSuccessHandler)과 앱 로그인·재발급(AuthController)이 함께 쓴다.

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team.codingforest.moyeota.auth.dto.TokenResponse;
import team.codingforest.moyeota.auth.entity.RefreshToken;
import team.codingforest.moyeota.auth.entity.User;
import team.codingforest.moyeota.auth.jwt.JWTUtil;
import team.codingforest.moyeota.auth.repository.RefreshRepository;
import team.codingforest.moyeota.auth.repository.UserRepository;

import java.util.Date;
import java.util.UUID;

@Service
public class TokenService {

    public static final long ACCESS_EXP  = 10 * 60 * 1000L;        //10분
    public static final long REFRESH_EXP = 24 * 60 * 60 * 1000L;   //24시간

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository;

    public TokenService(JWTUtil jwtUtil, RefreshRepository refreshRepository, UserRepository userRepository){
        this.jwtUtil=jwtUtil;
        this.refreshRepository=refreshRepository;
        this.userRepository=userRepository;
    }

    /*
    access/refresh를 발급하고, refresh만 DB에 기록한다.
    JWT는 서버가 취소할 수 없으므로, 로그아웃으로 무효화하려면 DB대조가 필요하다.

    username 자리에는 로그인 아이디가 아니라 publicId(UUID)가 들어온다.
    refresh_token은 user_id를 PK로 쓰므로 여기서 실제 User를 한 번 찾아와야 한다.

    행이 이미 있으면 새 토큰으로 덮어쓴다(사용자당 한 행).
    덕분에 재발급 때 옛 refresh는 자동으로 사라지고, 별도의 삭제 호출이 필요 없다.
    */
    @Transactional
    public TokenResponse issue(String username, String role){
        String access=jwtUtil.createJwt("access",username, role, ACCESS_EXP);
        String refresh=jwtUtil.createJwt("refresh",username, role, REFRESH_EXP);

        User user = findUser(username);

        //user_id가 PK이므로 findById로 그 사용자의 기존 행을 바로 찾을 수 있다.
        RefreshToken entity = refreshRepository.findById(user.getUserId())
                .orElseGet(() -> {
                    RefreshToken created = new RefreshToken();
                    //@MapsId가 이 user의 PK를 그대로 refresh_token의 PK로 복사해 간다.
                    created.setUser(user);
                    return created;
                });

        //이 행이 누구 것인지 publicId로도 남긴다. user.publicId와 같은 값이다.
        //orElseGet 안이 아니라 밖에서 넣는 이유는, 이 컬럼이 없던 시절에 만들어진 행도
        //다음 로그인 때 여기서 저절로 채워지게 하기 위해서다. 값은 언제 넣어도 같다.
        entity.setPublicId(user.getPublicId());

        entity.setRefreshToken(refresh);
        entity.setExpiration(new Date(System.currentTimeMillis()+REFRESH_EXP).toString());
        refreshRepository.save(entity);

        return new TokenResponse(access, refresh);
    }

    //토큰 안의 publicId로 사용자를 찾는다.
    //탈퇴한 사용자의 토큰이나 이 구조 이전에 발급된 옛 토큰은 여기서 걸러진다.
    private User findUser(String publicId){
        //UUID.fromString(null)은 NullPointerException이라 아래 catch에 걸리지 않는다. 먼저 막는다.
        if (publicId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token subject");
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(publicId);
        } catch (IllegalArgumentException e) {
            //이 구조로 바꾸기 전에 발급된 옛 토큰("google 1093847...")이 들어온 경우.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token subject");
        }

        return userRepository.findByPublicId(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));
    }
}
