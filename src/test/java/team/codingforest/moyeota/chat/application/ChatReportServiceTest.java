package team.codingforest.moyeota.chat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.chat.application.dto.ReportChatCommand;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatMessages;
import team.codingforest.moyeota.chat.domain.ChatReport;
import team.codingforest.moyeota.chat.domain.ChatReports;
import team.codingforest.moyeota.chat.domain.enums.ChatMessageStatus;
import team.codingforest.moyeota.chat.domain.enums.ChatMessageType;
import team.codingforest.moyeota.chat.domain.enums.ReportReasonType;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatReportServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long OTHER_ROOM_ID = 20L;
    private static final Long REPORTER_ID = 7L;
    private static final Long REPORTED_ID = 8L;
    private static final Long MESSAGE_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");

    @Mock
    private ChatReports chatReports;
    @Mock
    private ChatMessages chatMessages;
    @Mock
    private ChatRoomUserService chatRoomUserService;
    @InjectMocks
    private ChatReportService chatReportService;

    private ChatMessage messageIn(Long chatRoomId) {
        return ChatMessage.restore(MESSAGE_ID, chatRoomId, REPORTED_ID, "욕설",
                ChatMessageType.TEXT, ChatMessageStatus.ACTIVE, NOW, null);
    }

    private ReportChatCommand command() {
        return new ReportChatCommand(ROOM_ID, REPORTER_ID, MESSAGE_ID, ReportReasonType.ABUSE, null);
    }

    @Test
    void 신고를_저장한다() {
        given(chatMessages.findById(MESSAGE_ID)).willReturn(Optional.of(messageIn(ROOM_ID)));
        given(chatReports.existsByUserIdAndChatMessageId(REPORTER_ID, MESSAGE_ID)).willReturn(false);

        chatReportService.report(command());

        ArgumentCaptor<ChatReport> captor = ArgumentCaptor.captor();
        verify(chatReports).save(captor.capture());

        ChatReport saved = captor.getValue();
        assertThat(saved.getChatRoomId()).isEqualTo(ROOM_ID);
        assertThat(saved.getUserId()).isEqualTo(REPORTER_ID);
        assertThat(saved.getChatMessageId()).isEqualTo(MESSAGE_ID);
    }

    @Test
    void 피신고자는_요청이_아니라_메시지에서_가져온다() {
        given(chatMessages.findById(MESSAGE_ID)).willReturn(Optional.of(messageIn(ROOM_ID)));
        given(chatReports.existsByUserIdAndChatMessageId(REPORTER_ID, MESSAGE_ID)).willReturn(false);

        chatReportService.report(command());

        ArgumentCaptor<ChatReport> captor = ArgumentCaptor.captor();
        verify(chatReports).save(captor.capture());

        assertThat(captor.getValue().getReportedUserId()).isEqualTo(REPORTED_ID);
    }

    @Test
    void 참여자가_아니면_신고할_수_없다() {
        willThrow(new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                .given(chatRoomUserService).validateParticipant(REPORTER_ID, ROOM_ID);

        assertThatThrownBy(() -> chatReportService.report(command()))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT);

        verify(chatReports, never()).save(any());
    }

    @Test
    void 다른_방의_메시지는_신고할_수_없다() {
        given(chatMessages.findById(MESSAGE_ID)).willReturn(Optional.of(messageIn(OTHER_ROOM_ID)));

        assertThatThrownBy(() -> chatReportService.report(command()))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT);

        verify(chatReports, never()).save(any());
    }

    @Test
    void 없는_메시지는_신고할_수_없다() {
        given(chatMessages.findById(MESSAGE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatReportService.report(command()))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND);
    }

    @Test
    void 같은_메시지를_두_번_신고할_수_없다() {
        given(chatMessages.findById(MESSAGE_ID)).willReturn(Optional.of(messageIn(ROOM_ID)));
        given(chatReports.existsByUserIdAndChatMessageId(REPORTER_ID, MESSAGE_ID)).willReturn(true);

        assertThatThrownBy(() -> chatReportService.report(command()))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ALREADY_REPORTED);

        verify(chatReports, never()).save(any());
    }

    @Test
    void 동시_요청이_사전_조회를_함께_통과해도_중복으로_처리된다() {
        given(chatMessages.findById(MESSAGE_ID)).willReturn(Optional.of(messageIn(ROOM_ID)));
        given(chatReports.existsByUserIdAndChatMessageId(REPORTER_ID, MESSAGE_ID)).willReturn(false);
        willThrow(new DataIntegrityViolationException("uk_chat_report_user_message"))
                .given(chatReports).save(any());

        assertThatThrownBy(() -> chatReportService.report(command()))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ALREADY_REPORTED);
    }
}
