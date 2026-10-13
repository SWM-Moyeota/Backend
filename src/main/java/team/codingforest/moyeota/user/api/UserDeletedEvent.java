package team.codingforest.moyeota.user.api;

import java.util.UUID;

/**
 * 탈퇴 커밋 후 chat 과 place 가 받아 각자 정리함
 *
 * @param userId 탈퇴한 사용자의 내부 번호
 * @param publicId 탈퇴한 사용자의 공개 ID, 채팅방 나감 알림에 씀
 */
public record UserDeletedEvent(Long userId, UUID publicId) {
}
