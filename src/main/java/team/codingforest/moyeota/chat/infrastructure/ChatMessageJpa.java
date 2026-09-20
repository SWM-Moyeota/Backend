package team.codingforest.moyeota.chat.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatMessages;
import team.codingforest.moyeota.chat.domain.enums.ChatMessageStatus;
import team.codingforest.moyeota.chat.infrastructure.entity.ChatMessageEntity;

import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.infrastructure.search.SearchOutboxEntry;
import team.codingforest.moyeota.chat.infrastructure.search.SearchOutboxRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ChatMessageJpa implements ChatMessages {

    private final ChatMessageJpaRepository jpaRepository;
    private final SearchOutboxRepository searchOutbox;
    @org.springframework.beans.factory.annotation.Value("${chat.search.enabled:false}")
    private boolean searchEnabled;

    @Override
    public Optional<ChatMessage> findById(Long id) {
        return jpaRepository.findById(id).map(ChatMessageEntity::toDomain);
    }

    @Override
    @Transactional
    public ChatMessage save(ChatMessage chatMessage) {
        ChatMessageEntity entity = ChatMessageEntity.from(chatMessage);
        ChatMessage saved = jpaRepository.save(entity).toDomain();
        if (searchEnabled) {
            // 기존 행의 변경 잠금을 획득한 뒤 이벤트 버전을 발급한다.
            jpaRepository.flush();
            searchOutbox.save(SearchOutboxEntry.of(saved));
        }
        return saved;
    }

    @Override
    public List<ChatMessage> findByIds(List<Long> ids) {
        return jpaRepository.findAllById(ids).stream().map(ChatMessageEntity::toDomain).toList();
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

    @Override
    public List<ChatMessage> search(Long chatRoomId, String keyword, Long cursor, int size) {
        return jpaRepository.search(chatRoomId, ChatMessageStatus.ACTIVE, escapeLike(keyword), cursor, Limit.of(size))
                .stream()
                .map(ChatMessageEntity::toDomain)
                .toList();
    }

    private String escapeLike(String keyword) {
        return keyword.replace("!", "!!")   // 반드시 첫 줄
                .replace("%", "!%")
                .replace("_", "!_");
    }

    @Override
    public Map<Long, ChatMessage> findLatestByChatRoomIds(List<Long> chatRoomIds) {
        return jpaRepository.findLatestByChatRoomIds(chatRoomIds)
                .stream()
                .map(ChatMessageEntity::toDomain)
                .collect(Collectors.toMap(ChatMessage::getChatRoomId, m -> m));
    }

    @Override
    public Map<Long, Long> countUnreadByUserId(Long userId) {
        return jpaRepository.countUnreadByUserId(userId, ChatMessageStatus.ACTIVE).stream()
                .collect(Collectors.toMap(
                        ChatMessageJpaRepository.UnreadCount::getChatRoomId,
                        ChatMessageJpaRepository.UnreadCount::getUnreadCount));
    }
}