package team.codingforest.moyeota.user.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.application.UserFcmTokenService;
import team.codingforest.moyeota.user.application.UserProfileService;
import team.codingforest.moyeota.user.application.dto.RegisterFcmTokenRequest;
import team.codingforest.moyeota.user.application.dto.UpdateProfileRequest;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserFcmTokenService fcmTokenService;
    private final UserProfileService profileService;

    /** 닉네임·이미지 중 넘어온 것만 바꾼다. 둘 다 없으면 아무것도 안 하고 204 */
    @PatchMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProfile(@CurrentUser Long userId, @Valid @RequestBody UpdateProfileRequest request) {
        profileService.update(userId, request.nickname(), request.imageUrl());
    }

    @PutMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerFcmToken(@CurrentUser Long userId, @Valid @RequestBody RegisterFcmTokenRequest request) {
        fcmTokenService.register(userId, request.token());
    }

    @DeleteMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFcmToken(@CurrentUser Long userId) {
        fcmTokenService.remove(userId);
    }
}
