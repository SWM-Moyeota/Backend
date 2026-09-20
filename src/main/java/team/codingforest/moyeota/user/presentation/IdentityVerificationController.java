package team.codingforest.moyeota.user.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.application.IdentityVerificationService;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/users/me/identity-verifications")
@RequiredArgsConstructor
@Tag(name = "본인인증", description = "포트원 휴대폰 본인인증 요청 및 서버 검증")
public class IdentityVerificationController {
    private final IdentityVerificationService service;

    @PostMapping
    @Operation(summary = "본인인증 시작", description = "유효한 진행 중 요청이 있으면 재사용. 응답을 포트원 V2 SDK에 전달")
    public StartResponse start(@CurrentUser Long userId) {
        var request = service.start(userId);
        return new StartResponse(request.id(), request.storeId(), request.channelKey(), request.expiresAt());
    }

    @PostMapping("/{identityVerificationId}/complete")
    @Operation(summary = "본인인증 완료 검증", description = "클라이언트 인증 정보를 받지 않고 포트원에서 직접 조회. 동일 요청 재시도 가능")
    public IdentityVerificationService.Status complete(@CurrentUser Long userId, @PathVariable String identityVerificationId) {
        return service.complete(userId, identityVerificationId);
    }

    @GetMapping
    @Operation(summary = "내 본인인증 상태 조회")
    public IdentityVerificationService.Status status(@CurrentUser Long userId) { return service.status(userId); }

    public record StartResponse(String identityVerificationId, String storeId, String channelKey, Instant expiresAt) {}
}
