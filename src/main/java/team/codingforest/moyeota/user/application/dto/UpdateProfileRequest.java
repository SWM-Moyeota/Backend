package team.codingforest.moyeota.user.application.dto;

import jakarta.validation.constraints.Size;

/** 둘 다 선택 - null 인 필드는 건드리지 않는다 */
public record UpdateProfileRequest(@Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.") String nickname,
                                   @Size(max = 500) String imageUrl) {
}
