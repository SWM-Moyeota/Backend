package team.codingforest.moyeota.user.deletion.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.deletion.application.UserDeletionService;
import team.codingforest.moyeota.user.deletion.dto.UserDeletionRequest;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserDeletionController {
    private final UserDeletionService userDeletionService;

    /**
     * 비밀번호로 본인 확인 후 탈퇴함
     * 성공하면 기존 토큰이 모두 무효라 클라이언트는 저장한 토큰을 지워야 함
     *
     * @param userId 토큰에서 꺼낸 내부 사용자 번호
     * @param request 본인 확인용 현재 비밀번호
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser Long userId, @Valid @RequestBody UserDeletionRequest request) {
        userDeletionService.delete(userId, request.password());
    }
}
