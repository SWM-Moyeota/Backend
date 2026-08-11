package team.codingforest.moyeota.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import team.codingforest.moyeota.auth.CustomOAuth2User;
import team.codingforest.moyeota.auth.dto.UserDTO;

import java.io.IOException;

/*
Authorization: Bearer <accessToken> 헤더로만 인증한다. (웹/앱 공통)
- 만료 시 그냥 통과시키지 않고 401을 준다. 프론트가 재발급 시점을 알아야 하기 때문이다.
- category가 access가 아니면 거부한다. refresh를 access 자리에 넣는 것을 막는다.
 */
public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;

    public JWTFilter(JWTUtil jwtUtil) {

        this.jwtUtil = jwtUtil;
    }

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
