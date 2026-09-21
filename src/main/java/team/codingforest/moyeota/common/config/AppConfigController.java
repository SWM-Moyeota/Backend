package team.codingforest.moyeota.common.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "App Config", description = "앱 시작 시 받는 서버 설정")
@RequestMapping("/api/v1")
@RestController
public class AppConfigController {
    private final boolean taxiEnabled;

    public AppConfigController(@Value("${moyeota.taxi.enabled:true}") boolean taxiEnabled) {
        this.taxiEnabled = taxiEnabled;
    }

    @Operation(summary = "서버 설정 조회", description = "taxiEnabled=false 면 정원 충족 후 택시 호출 없이 채팅만 제공")
    @GetMapping("/config")
    public ResponseEntity<AppConfigResponse> config() {
        return ResponseEntity.ok(AppConfigResponse.from(taxiEnabled));
    }
}
