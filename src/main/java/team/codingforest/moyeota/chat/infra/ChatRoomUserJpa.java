package team.codingforest.moyeota.chat.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.domain.ChatRoomUserRepository;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomUserEntity;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomUserId;

import java.util.List;
import java.util.Optional;

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
    public List<ChatRoomUser> findActiveByUserId(Long userId){
        List<ChatRoomUserEntity> entities = jpaRepository.findByUserIdAndLeftAtIsNull(userId);
        return entities.stream()
                .map(ChatRoomUserEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<ChatRoomUser> findByUserIdAndChatRoomId(Long userId, Long chatRoomId) {
        Optional<ChatRoomUserEntity> entity = jpaRepository.findByUserIdAndChatRoomId(userId, chatRoomId);
        return entity.map(ChatRoomUserEntity::toDomain);
    }
}
