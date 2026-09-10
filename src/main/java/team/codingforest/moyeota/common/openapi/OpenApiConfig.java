package team.codingforest.moyeota.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *  Swagger UI: /swagger-ui.html, 스펙: /v3/api-docs
 *  - 모든 API 는 기본적으로 Bearer(access 토큰) 필요. /api/v1/auth/** 는 컨트롤러에서 @SecurityRequirements 로 제외
 *  - 4xx/5xx 응답은 전부 {code, message} 규격이라 커스터마이저가 ErrorResponse 스키마를 일괄로 붙인다
 */
@Configuration
public class OpenApiConfig {
    static final String BEARER = "bearerAuth";
    static final String ERROR_SCHEMA = "ErrorResponse";

    @Bean
    public OpenAPI moyeotaOpenApi() {
        Schema<?> errorSchema = new Schema<>().type("object")
                .addProperty("code", new Schema<>().type("string").description("에러 코드. common 은 enum 이름(PARTY_NOT_FOUND), user 는 USER001 형식").example("PARTY_NOT_FOUND"))
                .addProperty("message", new Schema<>().type("string").example("존재하지 않는 방입니다."));

        return new OpenAPI()
                .info(new Info().title("모여타 API").version("v1")
                        .description("""
                                목적지 기반 택시 동승 매칭 API.
                                - 인증: 로그인 후 받은 accessToken 을 `Authorization: Bearer {token}` 으로 보낸다. 신원(userId/driverId)은 토큰에서 추출하므로 요청에 싣지 않는다.
                                - 401 분기: code 가 USER002(만료)면 `POST /api/v1/auth/reissue` 로 재발급, 그 외 401 은 재로그인.
                                - 에러 응답: 항상 `{code, message}`.
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSchemas(ERROR_SCHEMA, errorSchema))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** 컨트롤러가 적은 4xx/5xx 응답에 ErrorResponse 스키마를 붙이고, 인증이 필요한 오퍼레이션에 401 을 일괄 추가 */
    @Bean
    public OpenApiCustomizer errorResponseCustomizer() {
        return openApi -> {
            if(openApi.getPaths() == null) return;

            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(op -> {
                boolean secured = op.getSecurity() == null || !op.getSecurity().isEmpty();   // 전역 요구사항 상속 = 인증 필요
                if(secured && !op.getResponses().containsKey("401")) {
                    op.getResponses().addApiResponse("401", new ApiResponse().description("인증 필요 - 토큰 없음/만료(USER002)/무효"));
                }
                op.getResponses().forEach((code, response) -> {
                    if(code.startsWith("4") || code.startsWith("5")) {
                        response.setContent(new Content().addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))));
                    }
                });
            }));
        };
    }
}
