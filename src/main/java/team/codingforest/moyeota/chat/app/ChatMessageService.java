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

    /**
     * 메시지 이력 조회 (커서 이전 => 과거 기록)
     * 채팅방에 들어가서 위로 스크롤 할때 사용
     */
    @Transactional(readOnly = true)
    public ChatMessageSlice findBefore(Long chatRoomId, Long cursor, int size) {
        validateSize(size);
        validateCursor(cursor);

        List<ChatMessage> messages = chatMessageRepository.findBefore(chatRoomId, cursor, size + 1);

        return toSlice(messages, size);
    }

    /**
     * 누락 메시지 조회 (커서 이후 => 최신 기록)
     * 연락이 끊겼을때 그동안 받지 못한 메시지 한 번에 수신
     */
    @Transactional(readOnly = true)
    public ChatMessageSlice findAfter(Long chatRoomId, Long cursor, int size) {
        validateSize(size);
        validateRequiredCursor(cursor);

        List<ChatMessage> messages = chatMessageRepository.findAfter(chatRoomId, cursor, size + 1);

        return toSlice(messages, size);
    }

    /**
     * 메시지 전송
     */
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

    /**
     * 메시지의 끝 여부를 조회 후 사이즈만큼 잘라서 반환
     */
    private ChatMessageSlice toSlice(List<ChatMessage> messages, int size) {
        boolean hasNext = messages.size() > size;
        List<ChatMessage> page = hasNext ? messages.subList(0, size) : messages;
        Long nextCursor = page.isEmpty() ? null : page.getLast().getId();

        return new ChatMessageSlice(
                page.stream().map(ChatMessageResult::from).toList(),
                nextCursor,
                hasNext
        );
    }
    /**
     * 요청 사이즈가 MIN과 MAX사이에 있는지 검증
     */
    private void validateSize(int size) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new ChatException(ChatErrorCode.CHAT_INVALID_PAGE_SIZE);
        }
    }

    /**
     * 커서 검증 (선택)
     * null 이면 첫 페이지 요청으로 구분
     */
    private void validateCursor(Long cursor) {
        if (cursor != null && cursor < 1) {
            throw new ChatException(ChatErrorCode.CHAT_INVALID_CURSOR);
        }
    }

    /**
     * 커서 검증 (필수)
     * 기준점이 없으면 조건이 성립하지 않음
     */
    private void validateRequiredCursor(Long cursor) {
        if (cursor == null || cursor < 1) {
            throw new ChatException(ChatErrorCode.CHAT_INVALID_CURSOR);
        }
    }
}
