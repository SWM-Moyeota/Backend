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

    publicId에는 사용자 UUID 문자열이 들어온다.
    refresh_token이 User를 FK로 참조하므로 여기서 실제 User를 한 번 찾아와야 한다.

    로그인할 때마다 새 refresh 행을 만든다. 따라서 같은 사용자가 여러 기기에서 로그인해도
    각 기기의 refresh가 서로를 덮어쓰지 않는다.
    */
    @Transactional
    public TokenResponse issue(String publicId){
        User user = findUser(publicId);

        String access=jwtUtil.createJwt("access",publicId,  ACCESS_EXP);
        String refresh=jwtUtil.createJwt("refresh",publicId, REFRESH_EXP);

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setPublicId(user.getPublicId());
        entity.setRefreshToken(refresh);
        entity.setExpiration(new Date(System.currentTimeMillis()+REFRESH_EXP).toString());
        refreshRepository.save(entity);

        return new TokenResponse(access, refresh);
    }

    /*
    재발급에 사용된 refresh 행 하나만 회전시킨다.
    사용자로 행을 찾으면 여러 기기의 토큰 중 어느 것을 바꿀지 알 수 없으므로,
    요청으로 받은 기존 refresh 문자열로 정확한 행을 찾는다.
    */
    @Transactional
    public TokenResponse rotate(String oldRefresh, String publicId) {
        User user = findUser(publicId);

        RefreshToken entity = refreshRepository.findByRefreshTokenForUpdate(oldRefresh)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token"));

        //JWT의 sub와 DB 행의 주인이 일치해야 한다.
        if (!entity.getUser().getUserId().equals(user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
        }

        String access = jwtUtil.createJwt("access", publicId, ACCESS_EXP);
        String refresh = jwtUtil.createJwt("refresh", publicId, REFRESH_EXP);

        entity.setPublicId(user.getPublicId());
        entity.setRefreshToken(refresh);
        entity.setExpiration(new Date(System.currentTimeMillis() + REFRESH_EXP).toString());
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
