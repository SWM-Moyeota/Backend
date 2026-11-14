package team.codingforest.moyeota.chat.message.infrastructure;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.common.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.common.exception.ChatException;
import team.codingforest.moyeota.chat.message.domain.ChatMessage;
import team.codingforest.moyeota.chat.message.domain.ChatMessages;
import team.codingforest.moyeota.chat.message.domain.enums.ChatMessageStatus;
import team.codingforest.moyeota.chat.message.infrastructure.entity.ChatMessageEntity;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ChatMessageJpa implements ChatMessages {

    private final ChatMessageJpaRepository jpaRepository;

    @Override
    public Optional<ChatMessage> findById(Long id) {
        return jpaRepository.findById(id).map(ChatMessageEntity::toDomain);
    }

    /**
     * 메시지를 저장함
     * 같은 사용자의 같은 clientMessageId 때문에 막히면 "처리 중" 예외로 바꿈
     * 그 밖의 위반(방 순번 중복 등)은 일어나면 안 되는 버그라 그대로 던짐
     *
     * @param chatMessage 저장할 메시지
     * @return 저장된 메시지
     */
    @Override
    public ChatMessage save(ChatMessage chatMessage) {
        ChatMessageEntity entity = ChatMessageEntity.from(chatMessage);
        try {
            jpaRepository.save(entity);   // id 를 DB 가 매겨서 INSERT 와 제약 위반이 여기서 바로 일어남
        } catch (DataIntegrityViolationException e) {
            if (isClientMessageIdDuplicate(e)) {
                throw new ChatException(ChatErrorCode.CHAT_MESSAGE_SENDING);   // 같은 키의 앞 요청이 아직 처리 중
            }
            throw e;
        }
        return entity.toDomain();
    }

    // 원인을 따라가 메시지 키 유일 제약에 걸렸는지 봄 - DB 마다 이름의 대소문자와 스키마 접두가 달라 소문자로 포함 여부를 봄
    private static boolean isClientMessageIdDuplicate(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName().toLowerCase(Locale.ROOT).contains(ChatMessageEntity.CLIENT_MESSAGE_ID_UNIQUE);
            }
        }
        return false;
    }

    @Override
    public Optional<ChatMessage> findByUserIdAndClientMessageId(Long userId, UUID clientMessageId) {
        return jpaRepository.findByUserIdAndClientMessageId(userId, clientMessageId).map(ChatMessageEntity::toDomain);
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