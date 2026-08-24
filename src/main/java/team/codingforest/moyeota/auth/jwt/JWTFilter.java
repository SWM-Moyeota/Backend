package team.codingforest.moyeota.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import team.codingforest.moyeota.auth.CustomOAuth2User;
import team.codingforest.moyeota.auth.dto.UserDTO;

import java.io.IOException;

/*
Authorization: Bearer <accessToken> 헤더로만 인증한다. (웹/앱 공통)
- 서명·형식이 깨진 토큰은 401로 막는다. 먼저 걸러내지 않으면 파싱 예외가 필터 밖으로 나가 500이 된다.
- 만료 시 그냥 통과시키지 않고 401을 준다. 프론트가 재발급 시점을 알아야 하기 때문이다.
- category가 access가 아니면 거부한다. refresh를 access 자리에 넣는 것을 막는다.

[401 본문으로 프론트가 할 일이 갈린다]
  "access token expired"  -> 토큰은 멀쩡한데 시간만 지났다. refresh로 재발급하면 된다.
  "invalid access token"  -> 못 믿을 토큰이다. 재발급해봐야 소용없으니 저장소를 비우고 로그인 화면으로 보낸다.
 */
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        //인증 API(로그인·재발급·로그아웃)는 access 없이 호출되므로 건너뛴다.
        //특히 재발급은 access가 만료된 상태로 들어오므로 여기서 401을 내면 안 된다.
        if (request.getRequestURI().startsWith("/api/v1/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");

        //토큰이 없거나 형식이 다르면 인증 없이 통과 (permitAll 경로용)
        //보호된 경로라면 뒤의 인가 단계에서 401로 막힌다.
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String accessToken = authorization.substring("Bearer ".length());

        /*
        서명·형식부터 확인한다. 반드시 다른 검사보다 먼저 와야 한다.
        아래 isExpired/getCategory/getUsername은 모두 파싱을 하는데,
        우리 키로 서명되지 않았거나 JWT 모양이 아닌 값이면 그 자리에서 예외가 나고
        그 예외가 필터를 뚫고 나가 500이 된다(401이어야 한다).
        */
        if (!jwtUtil.isSignatureValid(accessToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("invalid access token");
            return;
        }

        //만료됐으면 401 — 프론트가 이걸 보고 /api/auth/reissue를 호출한다
        if (jwtUtil.isExpired(accessToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("access token expired");
            return;
        }

        //refresh 토큰을 access 자리에 넣은 경우 거부
        if (!"access".equals(jwtUtil.getCategory(accessToken))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("invalid access token");
            return;
        }

        //토큰에서 username과 role 획득
        String username = jwtUtil.getUsername(accessToken);
        String role = jwtUtil.getRole(accessToken);

        //서명은 맞는데 sub가 비어 있는 토큰(우리 키로 만든 옛 도구의 산물 등).
        //그대로 흘려보내면 컨트롤러에서 UUID.fromString(null)로 NPE가 나 500이 된다.
        if (username == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("invalid access token");
            return;
        }

        //userDTO를 생성하여 값 set
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(username);
        userDTO.setRole(role);

        //UserDetails에 회원 정보 객체 담기
        CustomOAuth2User customOAuth2User = new CustomOAuth2User(userDTO);

        //스프링 시큐리티 인증 토큰 생성
        Authentication authToken = new UsernamePasswordAuthenticationToken(customOAuth2User, null, customOAuth2User.getAuthorities());
        //SecurityContext에 사용자 등록 (세션은 STATELESS라 요청 단위로만 유지된다)
        SecurityContextHolder.getContext().setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }
}
