package team.codingforest.moyeota.chat.room.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.infrastructure.entity.ChatRoomEntity;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ChatRoomJpa implements ChatRooms {
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
    public Map<Long, ChatRoom> findByIds(List<Long> chatRoomIds) {
        return jpaRepository.findAllById(chatRoomIds)
                .stream()
                .map(ChatRoomEntity::toDomain)
                .collect(Collectors.toMap(ChatRoom::getId, chatRoom -> chatRoom));
    }

    @Override
    public Optional<ChatRoom> findByPartyId(Long partyId) {
        return jpaRepository.findByPartyId(partyId).map(ChatRoomEntity::toDomain);
    }

    @Override
    public boolean existsByPartyId(Long partyId) {
        return jpaRepository.existsByPartyId(partyId);
    }

    @Override
    public Optional<ChatRoom> findByPartyIdForUpdate(Long partyId) {
        return jpaRepository.findByPartyIdForUpdate(partyId).map(ChatRoomEntity::toDomain);
    }
}
