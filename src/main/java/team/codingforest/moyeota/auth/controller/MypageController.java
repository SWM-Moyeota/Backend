package team.codingforest.moyeota.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import team.codingforest.moyeota.auth.CustomOAuth2User;
import team.codingforest.moyeota.auth.dto.MypageResponse;
import team.codingforest.moyeota.auth.dto.MypageUpdateRequest;
import team.codingforest.moyeota.auth.entity.User;
import team.codingforest.moyeota.auth.entity.UserProfile;
import team.codingforest.moyeota.auth.repository.UserProfileRepository;
import team.codingforest.moyeota.auth.repository.UserRepository;
import team.codingforest.moyeota.auth.service.UserService;

import java.util.UUID;

@Tag(name = "2. 마이페이지", description = "내 정보 조회 / 수정")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MypageController {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserService userService;

    @Operation(summary = "마이페이지 조회",
            description = "accessToken이 가리키는 사용자의 정보를 돌려준다. "
                    + "앱이 저장해둔 토큰이 아직 쓸 수 있는지 확인하는 용도로도 쓴다. "
                    + "토큰이 없거나 만료됐으면 401.")
    @GetMapping("/mypage")
    public MypageResponse mypage(@AuthenticationPrincipal CustomOAuth2User principal) {

        User user = findMypageUser(principal);

        return toResponse(user);
    }

    @Operation(summary = "마이페이지 수정",
            description = "닉네임을 수정한다. 보내지 않은 항목은 그대로 유지된다. "
                    + "닉네임은 1~20자여야 하며 어기면 400. "
                    + "이름과 이메일은 소셜 계정에서 가져오는 값이라 수정할 수 없다.")
    @PatchMapping("/mypage")
    public MypageResponse updateMypage(@AuthenticationPrincipal CustomOAuth2User principal,
                                       @RequestBody MypageUpdateRequest request) {

        User user = findMypageUser(principal);
        User updated = userService.updateProfile(user, request.nickname());

        //수정된 값을 그대로 돌려줘서 프론트가 다시 조회하지 않아도 되게 한다.
        return toResponse(updated);
    }

    /*
    신원은 항상 토큰에서만 가져온다.
    프론트가 보낸 userId 같은 값은 얼마든지 위조할 수 있으므로 파라미터로 받지 않는다.
    수정 API에서 특히 중요하다. 이렇게 해야 남의 마이페이지를 고칠 방법이 아예 없다.

    @AuthenticationPrincipal 로 받는 CustomOAuth2User는 JWTFilter가 SecurityContext에 넣어둔 것이다.
    여기까지 실행됐다는 건 이미 필터에서 토큰 검증(서명·만료·category)을 통과했다는 뜻이다.

    주의: principal.getName()은 null이다.
    CustomOAuth2User.getName()이 userDTO.getName()을 반환하는데
    JWTFilter는 username과 role만 채우기 때문이다. 반드시 getUsername()을 쓴다.
    여기서 getUsername()이 담고 있는 값은 User.publicId(UUID) 문자열이다.
     */
    private User findMypageUser(CustomOAuth2User principal) {

        //UUID.fromString(null)은 IllegalArgumentException이 아니라 NullPointerException을 던진다.
        //아래 catch로는 안 걸려서 500이 되므로 null은 여기서 먼저 막는다.
        if (principal.getUsername() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token subject");
        }

        UUID publicId;
        try {
            publicId = UUID.fromString(principal.getUsername());
        } catch (IllegalArgumentException e) {
            //이 구조로 바꾸기 전에 발급된 옛 토큰("google 1093847...")이 들어온 경우.
            //형식부터 다르므로 다시 로그인하라고 알려준다.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token subject");
        }

        //토큰은 멀쩡한데 DB에 사용자가 없는 경우(탈퇴 등).
        //access는 서버가 취소할 수 없어 만료 전까지 살아있으므로 여기서 걸러준다.
        return userRepository.findByPublicId(publicId)
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
