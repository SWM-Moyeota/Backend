package team.codingforest.moyeota.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import team.codingforest.moyeota.auth.dto.MypageResponse;
import team.codingforest.moyeota.auth.dto.MypageUpdateRequest;
import team.codingforest.moyeota.auth.entity.User;
import team.codingforest.moyeota.auth.entity.UserProfile;
import team.codingforest.moyeota.auth.repository.UserProfileRepository;
import team.codingforest.moyeota.auth.repository.UserRepository;
import team.codingforest.moyeota.auth.service.UserService;
import team.codingforest.moyeota.auth.web.LoginUser;
import team.codingforest.moyeota.auth.web.UserContext;

@Tag(name = "2. 마이페이지", description = "내 정보 조회 / 수정")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MypageController {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    /*
    신원은 항상 토큰에서만 가져온다.
    프론트가 보낸 userId 같은 값은 얼마든지 위조할 수 있으므로 파라미터로 받지 않는다.
    수정 API에서 특히 중요하다. 이렇게 해야 남의 마이페이지를 고칠 방법이 아예 없다.

    [신원을 얻는 경로 — 인터셉터 방식]
    UserInterceptor가 요청 초입에서 토큰 -> publicId -> user_id 변환을 끝내고
    UserContext(요청 스레드에 붙은 선반)에 LoginUser를 올려둔다.
    여기서는 그 선반에서 꺼내 쓰기만 한다. 여기까지 실행됐다는 건 JWTFilter의 토큰 검증을
    이미 통과했다는 뜻이다.

    (참고) @CurrentUser 리졸버 방식도 코드로 남아 있지만 지금은 등록을 꺼둔 상태다.
    나중에 두 방식을 비교하려고 남겨둔 것이다. WebConfig에서 한쪽만 켜서 쓴다.
    */
    @Operation(summary = "마이페이지 조회",
            description = "accessToken이 가리키는 사용자의 정보를 돌려준다. "
                    + "앱이 저장해둔 토큰이 아직 쓸 수 있는지 확인하는 용도로도 쓴다. "
                    + "토큰이 없거나 만료됐으면 401.")
    @GetMapping("/mypage")
    public MypageResponse mypage() {

        return toResponse(currentUser());
    }

    @Operation(summary = "마이페이지 수정",
            description = "닉네임을 수정한다. 보내지 않은 항목은 그대로 유지된다. "
                    + "닉네임은 1~20자여야 하며 어기면 400. "
                    + "이름과 이메일은 소셜 계정에서 가져오는 값이라 수정할 수 없다.")
    @PatchMapping("/mypage")
    public MypageResponse updateMypage(@RequestBody MypageUpdateRequest request) {

        User updated = userService.updateProfile(currentUser(), request.nickname());

        //수정된 값을 그대로 돌려줘서 프론트가 다시 조회하지 않아도 되게 한다.
        return toResponse(updated);
    }

    /*
    UserContext(선반)에서 신원을 꺼내 사용자 본체를 조회한다.

    선반에는 id만 들어 있다(user_id, publicId). 마이페이지는 이름·닉네임 등 본체가 필요하므로
    여기서 user_id로 한 번 더 조회한다.
    (인터셉터가 publicId로 이미 한 번 조회했으니, 본체가 필요한 API에서는 조회가 두 번 일어난다.
     신원만 필요한 API는 UserContext.get()의 id만 쓰면 조회 없이 끝난다.)

    선반이 비어 있는 경우:
    - 보호된 경로라면 스프링 시큐리티 인가 단계가 이미 401로 막았을 것이라 여기 오지 않는다.
    - permitAll 경로에서 토큰 없이 들어오면 선반이 비므로 여기서 401로 막는다.
    */
    private User currentUser() {

        LoginUser me = UserContext.get();
        if (me == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }

        //토큰은 멀쩡한데 그 사이 DB에서 사용자가 사라진 경우(탈퇴 등)는 401로 막는다.
        return userRepository.findById(me.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));
    }

    private MypageResponse toResponse(User user) {

        //user_profile은 user_id를 그대로 PK로 쓰므로 findById로 바로 찾는다.
        UserProfile profile = userProfileRepository.findById(user.getUserId()).orElse(null);

        return new MypageResponse(
                user.getPublicId(),
                profile != null ? profile.getName() : null,
                profile != null ? profile.getEmail() : null,
                user.getNickname(),
                user.getImageUrl());
    }
}
