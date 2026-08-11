package team.codingforest.moyeota.auth.service;

//토큰 발급 규칙(만료시간 상수, refresh의 DB 저장)을 한 곳에 모아둔 자리.
//웹 로그인(CustomSuccessHandler)과 앱 로그인·재발급(AuthController)이 함께 쓴다.

import org.springframework.stereotype.Service;
import team.codingforest.moyeota.auth.dto.TokenResponse;
import team.codingforest.moyeota.auth.entity.RefreshEntity;
import team.codingforest.moyeota.auth.jwt.JWTUtil;
import team.codingforest.moyeota.auth.repository.RefreshRepository;

import java.util.Date;

@Service
public class TokenService {

    public static final long ACCESS_EXP  = 10 * 60 * 1000L;        //10분
    public static final long REFRESH_EXP = 24 * 60 * 60 * 1000L;   //24시간

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;

    public TokenService(JWTUtil jwtUtil, RefreshRepository refreshRepository){
        this.jwtUtil=jwtUtil;
        this.refreshRepository=refreshRepository;
    }

    //access/refresh를 발급하고, refresh만 DB에 기록한다.
    //JWT는 서버가 취소할 수 없으므로, 로그아웃으로 무효화하혀면 DB대조가 필요하다
    public TokenResponse issue(String username, String role){
        String access=jwtUtil.createJwt("access",username, role, ACCESS_EXP);
        String refresh=jwtUtil.createJwt("refresh",username, role, REFRESH_EXP);

        RefreshEntity entity = new RefreshEntity();
        entity.setUsername(username);
        entity.setRefresh(refresh);
        entity.setExpiration(new Date(System.currentTimeMillis()+REFRESH_EXP).toString());
        refreshRepository.save(entity);
        return new TokenResponse(access, refresh);
    }
}
