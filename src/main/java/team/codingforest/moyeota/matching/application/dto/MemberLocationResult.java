package team.codingforest.moyeota.matching.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** 동승자 한 명의 실시간 위치. 식별자는 방 상세의 members[].publicId 와 같은 값이라 앱이 이름·프로필로 잇는다 */
public record MemberLocationResult(
        @Schema(description = "동승자 공개 식별자(방 상세 members[].publicId 와 동일)") UUID publicId,
        @Schema(example = "스모크일") String nickname,
        @Schema(example = "35.1579") double latitude,
        @Schema(example = "129.0596") double longitude
) {
}
