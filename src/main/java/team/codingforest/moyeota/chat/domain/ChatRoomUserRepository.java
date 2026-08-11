package team.codingforest.moyeota.chat.domain;

import java.util.List;

public interface ChatRoomUserRepository {
    ChatRoomUser save(ChatRoomUser chatRoomUser);

    List<ChatRoomUser> findActiveRoom(Long userId);

}
