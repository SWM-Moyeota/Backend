package team.codingforest.moyeota.user.deletion.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "유저")
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
    @Operation(summary = "회원 탈퇴", description = """
            현재 비밀번호로 본인 확인 후 탈퇴. 대상은 토큰의 사용자
            - DELETE 지만 본문 필수. Android Retrofit 은 `@DELETE` 대신 `@HTTP(method = "DELETE", path = "api/v1/users/me", hasBody = true)`
            - 성공(204) 시 기존 access·refresh 토큰 전부 무효. 저장한 토큰을 지우고 로그인 화면으로
            - FCM 토큰은 서버가 비우므로 DELETE /users/me/fcm-token 을 따로 부를 필요 없음
            - 진행 중인 합승(참여자 또는 배정된 기사)이 있으면 409. 합승을 자동으로 나가게 하지 않음
            - 탈퇴 즉시 개인정보 삭제, 아이디는 바로 재사용 가능. 같은 전화번호는 30일간 재가입 불가(REJOIN_RESTRICTED, USER109)
            - 기사 등록 정보(계좌·자격번호·차량)도 삭제. 채팅·합승 기록에는 "탈퇴한 사용자"로 표시
            """)
    @ApiResponses({@ApiResponse(responseCode = "204", description = "탈퇴 완료. 본문 없음"), @ApiResponse(responseCode = "400", description = "PASSWORD_MISMATCH(USER111) - 비밀번호 불일치 / INVALID_REQUEST - 본문 없음·password 누락·공백"), @ApiResponse(responseCode = "401", description = "토큰 없음(USER005) / 만료(USER002) / 무효·탈퇴한 사용자의 토큰(USER001)"), @ApiResponse(responseCode = "409", description = "ONGOING_PARTY_EXISTS(USER110) - 진행 중인 합승(참여자 또는 배정된 기사)이 있음")})
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser Long userId, @Valid @RequestBody UserDeletionRequest request) {
        userDeletionService.delete(userId, request.password());
    }
}
