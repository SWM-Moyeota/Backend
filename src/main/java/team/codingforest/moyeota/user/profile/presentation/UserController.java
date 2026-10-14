package team.codingforest.moyeota.user.profile.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.profile.application.ProfileImages;
import team.codingforest.moyeota.user.profile.application.UserFcmTokenService;
import team.codingforest.moyeota.user.profile.application.UserProfileService;
import team.codingforest.moyeota.user.profile.dto.ProfileImageResponse;
import team.codingforest.moyeota.user.profile.dto.RegisterFcmTokenRequest;
import team.codingforest.moyeota.user.profile.dto.UpdateProfileRequest;

import java.util.List;

@Tag(name = "유저", description = "공개 프로필 수정, 승객 앱 FCM 토큰")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserFcmTokenService fcmTokenService;
    private final UserProfileService profileService;
    private final ProfileImages profileImages;

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

    @Operation(summary = "프로필 이미지 목록", description = "고를 수 있는 제공 이미지. 앱은 이 순서대로 보여주고, 고른 key 를 프로필 수정의 imageKey 로 보냄")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공")})
    @GetMapping("/profile-images")
    public List<ProfileImageResponse> getProfileImages() {
        return profileImages.presets();
    }

}
