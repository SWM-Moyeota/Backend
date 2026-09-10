package team.codingforest.moyeota.user.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.application.UserFcmTokenService;
import team.codingforest.moyeota.user.application.UserProfileService;
import team.codingforest.moyeota.user.application.dto.RegisterFcmTokenRequest;
import team.codingforest.moyeota.user.application.dto.UpdateProfileRequest;

@Tag(name = "유저", description = "공개 프로필 수정, 승객 앱 FCM 토큰")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserFcmTokenService fcmTokenService;
    private final UserProfileService profileService;

    /** 닉네임·이미지 중 넘어온 것만 바꾼다. 둘 다 없으면 아무것도 안 하고 204 */
    @Operation(summary = "프로필 수정", description = "nickname·imageUrl 중 넘어온 것만 변경. 자기 닉네임 재전송은 중복 아님")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "수정 완료"), @ApiResponse(responseCode = "400", description = "INVALID_NICKNAME(USER107)"), @ApiResponse(responseCode = "409", description = "NICKNAME_DUPLICATED(USER108)")})
    @PatchMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProfile(@CurrentUser Long userId, @Valid @RequestBody UpdateProfileRequest request) {
        profileService.update(userId, request.nickname(), request.imageUrl());
    }

    @Operation(summary = "승객 앱 FCM 토큰 등록/갱신", description = "로그인 직후와 onTokenRefresh 때. 기사 앱 토큰(/drivers/fcm-token)과 별개")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "저장 완료"), @ApiResponse(responseCode = "400", description = "EMPTY_FCM_TOKEN(USER106)")})
    @PutMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerFcmToken(@CurrentUser Long userId, @Valid @RequestBody RegisterFcmTokenRequest request) {
        fcmTokenService.register(userId, request.token());
    }

    @Operation(summary = "승객 앱 FCM 토큰 삭제", description = "로그아웃 직전 호출. 멱등")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "삭제 완료")})
    @DeleteMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFcmToken(@CurrentUser Long userId) {
        fcmTokenService.remove(userId);
    }
}
