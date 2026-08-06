package team.codingforest.moyeota.chat.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomRepository;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomEntity;

@Repository
@RequiredArgsConstructor
public class ChatRoomJpa implements ChatRoomRepository {
    private final ChatRoomJpaRepository jpaRepository;

    @Override
    public ChatRoom save(ChatRoom chatRoom) {
        ChatRoomEntity entity = ChatRoomEntity.from(chatRoom);
        jpaRepository.save(entity);
        return entity.toDomain();
    }
}
