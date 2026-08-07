package team.codingforest.moyeota.chat.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.domain.ChatRoomUserRepository;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomUserEntity;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatRoomUserJpa implements ChatRoomUserRepository {

    private final ChatRoomUserJpaRepository jpaRepository;

    @Override
    public ChatRoomUser save(ChatRoomUser chatRoomUser) {
        ChatRoomUserEntity entity = ChatRoomUserEntity.from(chatRoomUser);
        jpaRepository.save(entity);
        return entity.toDomain();
    }

    @Override
    public List<ChatRoomUser> findActiveRoom(Long userId) {
        return jpaRepository.findByUserIdAndLeftAtIsNull(userId).stream()
                .map(ChatRoomUserEntity::toDomain)
                .toList();
    }
}
