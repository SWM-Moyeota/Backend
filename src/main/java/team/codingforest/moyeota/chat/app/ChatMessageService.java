package team.codingforest.moyeota.chat.app;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.app.dto.ChatMessageResult;
import team.codingforest.moyeota.chat.app.dto.ChatMessageSlice;
import team.codingforest.moyeota.chat.app.dto.SendMessageCommand;
import team.codingforest.moyeota.chat.app.event.ChatMessageSentEvent;
import team.codingforest.moyeota.chat.domain.*;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomUserRepository chatRoomUserRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    @Transactional(readOnly = true)
    public ChatMessageSlice findBefore(Long chatRoomId, Long cursor, int size) {
        validateSize(size);
        validateCursor(cursor);

        List<ChatMessage> messages = chatMessageRepository.findBefore(chatRoomId, cursor, size + 1);

        boolean hasNext = messages.size() > size;
        List<ChatMessage> page = hasNext ? messages.subList(0, size) : messages;
        Long nextCursor = page.isEmpty() ? null : page.getLast().getId();

        return new ChatMessageSlice(
                page.stream().map(ChatMessageResult::from).toList(),
                nextCursor,
                hasNext
        );
    }

    @Transactional
    public ChatMessageResult sendMessage(SendMessageCommand command) {
        ChatRoom chatRoom = chatRoomRepository.findById(command.chatRoomId())
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));

        chatRoom.validateCanSend();

        chatRoomUserRepository.findActiveByUserIdAndChatRoomId(command.userId(), command.chatRoomId())
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT));

        ChatMessage message = ChatMessage.text(
                command.chatRoomId(),
                command.userId(),
                command.content(),
                Instant.now()
        );

        ChatMessageResult result = ChatMessageResult.from(chatMessageRepository.save(message));

        eventPublisher.publishEvent(new ChatMessageSentEvent(result));

        return result;
    }

    private void validateSize(int size) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new ChatException(ChatErrorCode.CHAT_INVALID_PAGE_SIZE);
        }
    }

    private void validateCursor(Long cursor) {
        if (cursor != null && cursor < 1) {
            throw new ChatException(ChatErrorCode.CHAT_INVALID_CURSOR);
        }
    }
}
