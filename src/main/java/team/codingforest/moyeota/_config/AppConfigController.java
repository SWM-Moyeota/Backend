package team.codingforest.moyeota._config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "App Config", description = "앱 시작 시 받는 서버 설정")
@SecurityRequirements
@RequestMapping("/api/v1")
@RestController
public class AppConfigController {
    /** 기사 없이 승객끼리만 모이는 서비스라 늘 false. 출시 앱이 이 키로 화면을 가르므로 키는 남긴다 */
    private static final AppConfigResponse PASSENGER_ONLY = AppConfigResponse.from(false);

    @Operation(summary = "서버 설정 조회", description = "taxiEnabled 는 늘 false - 정원이 차도 택시 호출 없이 채팅으로 모인다. 출시 앱 호환을 위해 키를 유지한다")
    @GetMapping("/config")
    public ResponseEntity<AppConfigResponse> config() {
        return ResponseEntity.ok(PASSENGER_ONLY);
    }
}
