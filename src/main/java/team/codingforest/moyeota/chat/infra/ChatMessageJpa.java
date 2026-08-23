package team.codingforest.moyeota.chat.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatMessageRepository;
import team.codingforest.moyeota.chat.infra.entity.ChatMessageEntity;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatMessageJpa implements ChatMessageRepository {

    private final ChatMessageJpaRepository jpaRepository;

    public Optional<ChatMessage> findById(Long id) {
        return jpaRepository.findById(id).map(ChatMessageEntity::toDomain);
    }

    @Override
    public ChatMessage save(ChatMessage chatMessage) {
        ChatMessageEntity entity = ChatMessageEntity.from(chatMessage);
        jpaRepository.save(entity);
        return entity.toDomain();
    }

    @Override
    public List<ChatMessage> findBefore(Long chatRoomId, Long cursor, int size) {
        Limit limit = Limit.of(size);
        List<ChatMessageEntity> entities = cursor == null
                ? jpaRepository.findByChatRoomIdOrderByIdDesc(chatRoomId, limit)
                : jpaRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(chatRoomId, cursor, limit);

        return entities.stream()
                .map(ChatMessageEntity::toDomain)
                .toList();
    }

    @Override
    public List<ChatMessage> findAfter(Long chatRoomId, Long cursor, int size) {
        return jpaRepository.findByChatRoomIdAndIdGreaterThanOrderByIdAsc(chatRoomId, cursor, Limit.of(size))
                .stream()
                .map(ChatMessageEntity::toDomain)
                .toList();
    }
}