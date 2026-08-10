package team.codingforest.moyeota.chat.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomRepository;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomEntity;

import java.util.Optional;
import java.util.OptionalInt;

@Repository
@RequiredArgsConstructor
public class ChatRoomJpa implements ChatRoomRepository {
    private final ChatRoomJpaRepository jpaRepository;

    @Override
    public ChatRoom save(ChatRoom chatRoom) {
        ChatRoomEntity entity = ChatRoomEntity.from(chatRoom);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<ChatRoom> findById(Long chatRoomId) {
        return jpaRepository.findById(chatRoomId).map(ChatRoomEntity::toDomain);
    }

    @Override
    public boolean existsByPartyId(Long partyId) {
        return jpaRepository.existsByPartyId(partyId);
    }
}
