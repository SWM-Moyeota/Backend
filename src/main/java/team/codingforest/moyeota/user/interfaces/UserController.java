package team.codingforest.moyeota.user.interfaces;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.application.UserFcmTokenService;
import team.codingforest.moyeota.user.application.dto.RegisterFcmTokenRequest;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserFcmTokenService fcmTokenService;

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
