package team.codingforest.moyeota.user.local.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import team.codingforest.moyeota.user.local.dto.BillingCustomerResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.local.application.LocalUserService;
import team.codingforest.moyeota.user.local.dto.UserResponse;

@Tag(name = "내 정보", description = "본인 프로필 조회")
@RestController
@RequestMapping("/api/v1/local")
@RequiredArgsConstructor
public class LocalUserController {
    private final LocalUserService service;

    @Operation(summary = "카드 등록용 본인 정보", description = "로그인한 회원의 실명·전화번호·이메일을 SDK 전달용으로 조회")
    @GetMapping("/users/billing-customer")
    public ResponseEntity<BillingCustomerResponse> getBillingCustomer(@CurrentUser Long userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.getBillingCustomer(userId));
    }

    @Operation(summary = "내 프로필", description = "publicId 와 표시 이름(닉네임, 없으면 실명)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "프로필"), @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND(USER103)")})
    @GetMapping("/users/info")
    public ResponseEntity<UserResponse> getProfile(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.getProfile(userId));
    }
}