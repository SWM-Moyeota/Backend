package team.codingforest.moyeota.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {

        //Swagger 화면 우측 상단 Authorize 버튼에서 access 토큰을 넣을 수 있게 한다.
        //값에는 "Bearer "를 빼고 토큰만 붙여넣으면 되며, Swagger가 알아서 접두사를 붙여 보낸다.
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("로그인 후 받은 accessToken을 붙여넣으세요. (Bearer 접두사 없이 토큰만)");

        return new OpenAPI()
                .info(new Info()
                        .title("모여타 API")
                        .version("v0.0.1")
                        .description("""
                                로그인 후 발급받은 JWT를 Authorization 헤더로 보내는 방식입니다.

                                사용 순서
                                1. 브라우저 주소창에서 GET /api/v1/auth/google 로 이동해 구글 로그인을 한다
                                   (또는 POST /api/v1/auth/login 으로 로컬 로그인)
                                2. 리다이렉트된 주소의 #accessToken= 뒤 값을 복사한다
                                3. 우측 상단 Authorize에 accessToken을 넣는다
                                4. 보호된 API를 호출한다
                                5. 401 access token expired가 나오면 POST /api/v1/auth/reissue 로 갱신한다

                                accessToken 만료 10분 / refreshToken 만료 24시간
                                """))
                .components(new Components().addSecuritySchemes(BEARER, bearerScheme))
                //기본적으로 모든 API에 토큰을 적용하고, 로그인처럼 토큰이 필요 없는 곳은
                //컨트롤러에서 @SecurityRequirements 로 개별 해제한다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
