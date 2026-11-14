package team.codingforest.moyeota._config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "App Config", description = "앱 시작 시 받는 서버 설정")
@SecurityRequirements
@RequestMapping("/api/v1")
@RestController
@RequiredArgsConstructor
public class AppConfigController {
    /** 기사 없이 승객끼리만 모이는 서비스라 늘 false. 출시 앱이 이 키로 화면을 가르므로 키는 남긴다 */
    private static final boolean TAXI_ENABLED = false;

    private final AppProperties appProperties;

    @Operation(summary = "서버 설정 조회", description = """
            앱이 시작할 때 한 번 받는다. 인증은 필요 없다 - 앱의 업데이트 게이트가 로그인 화면보다 앞에 있어서 로그인하지 않은 사용자도 막혀야 한다.

            - `taxiEnabled` 는 늘 false. 정원이 차도 택시 호출 없이 채팅으로 모인다. 출시 앱 호환을 위해 키를 유지한다
            - `minVersionCode` 미달인 앱은 빠져나갈 수 없는 업데이트 화면에서 멈춘다. **같은 값은 통과한다**(경계가 `<`)
            - `recommendedVersionCode` 미달이면 막지 않고 한 번 알린다

            버전 두 개는 안드로이드 `versionCode` 와 같은 정수(`YYMMDD*100+N`)이고, **설정하지 않으면 응답에서 키가 빠진다**(기준 없음).
            환경변수 `MIN_VERSION_CODE`·`RECOMMENDED_VERSION_CODE` 로 넣고, 값을 비우면 코드 배포 없이 되돌릴 수 있다.
            잘못된 큰 값은 전원을 업데이트 화면에 잠그고 앱 쪽에서는 되돌릴 수 없으니 반드시 환경변수로 다룬다.
            """)
    @GetMapping("/config")
    public ResponseEntity<AppConfigResponse> config() {
        return ResponseEntity.ok(AppConfigResponse.of(TAXI_ENABLED, appProperties));
    }
}
