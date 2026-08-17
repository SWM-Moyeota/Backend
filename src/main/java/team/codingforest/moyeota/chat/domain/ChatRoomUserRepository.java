package team.codingforest.moyeota.chat.domain;

import java.util.List;
import java.util.Optional;

public interface ChatRoomUserRepository {
    ChatRoomUser save(ChatRoomUser chatRoomUser);
    List<ChatRoomUser> findActiveByUserId(Long userId);
    Optional<ChatRoomUser> findByUserIdAndChatRoomId(Long userId, Long chatRoomId);

}
