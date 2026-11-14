package team.codingforest.moyeota.user.passwordreset.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.user.passwordreset.application.*;
import team.codingforest.moyeota.user.passwordreset.dto.*;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
@Tag(name="비밀번호 재설정", description="등록 휴대폰 인증 후 일회용 권한으로 비밀번호 재설정")
@SecurityRequirements
@RequiredArgsConstructor
@ApiResponses({@ApiResponse(responseCode="400", description="입력 또는 인증 실패"),
        @ApiResponse(responseCode="429", description="요청 제한, Retry-After 초 후 재시도"),
        @ApiResponse(responseCode="503", description="인증 저장소 일시 장애")})
public class PasswordResetController {
    private final PasswordResetSendService sendService;
    private final PasswordResetVerifyService verifyService;
    private final PasswordResetConfirmService confirmService;

    @PostMapping("/send")
    @Operation(summary="재설정 인증번호 요청", description="계정 일치 여부와 무관하게 같은 접수 응답. 실제 발송은 별도 작업으로 처리")
    @ApiResponse(responseCode="202", description="접수번호, 유효시간 180초, 재발송 간격 60초")
    public ResponseEntity<PasswordResetSendResponse> send(@Valid @RequestBody PasswordResetSendRequest body) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(sendService.send(body));
    }

    @PostMapping("/verify")
    @Operation(summary="재설정 인증번호 확인", description="접수번호의 코드를 한 번만 검증하고 10분 유효한 resetToken 발급")
    @ApiResponse(responseCode="200", description="일회용 resetToken과 expiresIn=600")
    public ResponseEntity<PasswordResetVerifyResponse> verify(@Valid @RequestBody PasswordResetVerifyRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(verifyService.verify(body));
    }

    @PostMapping("/confirm")
    @Operation(summary="새 비밀번호 저장", description="두 비밀번호 서버 검증. 비밀번호·인증 권한·refresh 취소를 함께 저장. 기존 access는 만료까지 유효")
    @ApiResponse(responseCode="204", description="변경 완료, 다시 로그인")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest body) {
        confirmService.confirm(body);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
