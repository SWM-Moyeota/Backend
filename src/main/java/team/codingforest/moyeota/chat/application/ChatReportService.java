package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.application.dto.ReportChatCommand;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatMessages;
import team.codingforest.moyeota.chat.domain.ChatReport;
import team.codingforest.moyeota.chat.domain.ChatReports;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatReportService {

    private final ChatReports chatReports;
    private final ChatMessages chatMessages;
    private final ChatRoomUserService chatRoomUserService;

    @Transactional
    public void report(ReportChatCommand command) {
        chatRoomUserService.validateParticipant(command.userId(), command.chatRoomId());

        ChatMessage message = chatMessages.findById(command.chatMessageId())
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND));

        if (!message.getChatRoomId().equals(command.chatRoomId())) {
            throw new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT);
        }

        if (chatReports.existsByUserIdAndChatMessageId(command.userId(), command.chatMessageId())) {
            throw new ChatException(ChatErrorCode.CHAT_ALREADY_REPORTED);
        }

        try {
            chatReports.save(ChatReport.create(
                    message.getChatRoomId(),
                    command.userId(),
                    message.getUserId(),
                    command.chatMessageId(),
                    command.reason(),
                    command.description(),
                    Instant.now()));
        } catch (DataIntegrityViolationException e) {
            throw new ChatException(ChatErrorCode.CHAT_ALREADY_REPORTED);
        }

        log.info("채팅 신고 접수 chatRoomId={} messageId={} reporterId={} reason={}",
                command.chatRoomId(), command.chatMessageId(), command.userId(), command.reason());
    }
}
