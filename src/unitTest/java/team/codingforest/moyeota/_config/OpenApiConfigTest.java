package team.codingforest.moyeota._config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    private OpenAPI openApiWith(Operation operation) {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(OpenApiConfig.BEARER))
                .paths(new Paths().addPathItem("/test", new PathItem().get(operation)));
    }

    private Operation operation() {
        return new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("ok")));
    }

    @Test
    void 전역_Bearer를_따르는_API에는_401이_붙는다() {
        Operation op = operation();

        config.errorResponseCustomizer().customise(openApiWith(op));

        assertThat(op.getResponses()).containsKey("401");
    }

    @Test
    void 인증을_해제한_API에는_401이_붙지_않는다() {
        Operation op = operation().security(List.of());

        config.errorResponseCustomizer().customise(openApiWith(op));

        assertThat(op.getResponses()).doesNotContainKey("401");
    }

    @Test
    void 컨트롤러가_적은_401_설명은_덮어쓰지_않는다() {
        Operation op = operation();
        op.getResponses().addApiResponse("401", new ApiResponse().description("TOKEN_INVALID(USER001)"));

        config.errorResponseCustomizer().customise(openApiWith(op));

        assertThat(op.getResponses().get("401").getDescription()).isEqualTo("TOKEN_INVALID(USER001)");
    }
}
