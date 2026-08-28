package team.codingforest.moyeota.chat.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import team.codingforest.moyeota.chat.app.dto.ChatMessageResult;
import team.codingforest.moyeota.chat.app.dto.ChatMessageSlice;
import team.codingforest.moyeota.chat.app.dto.FindMessageCommand;
import team.codingforest.moyeota.chat.app.dto.SearchMessageCommand;
import team.codingforest.moyeota.chat.app.dto.SendMessageCommand;
import team.codingforest.moyeota.chat.app.event.ChatMessageDeleteEvent;
import team.codingforest.moyeota.chat.app.event.ChatMessageSentEvent;
import team.codingforest.moyeota.chat.domain.*;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {
    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomUserService chatRoomUserService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private ChatMessageService chatMessageService;

    private ChatMessage message(Long id) {
        return ChatMessage.restore(id, ROOM_ID, USER_ID, "내용", ChatMessageType.TEXT, ChatMessageStatus.ACTIVE, NOW, null);
    }

    private ChatRoom room(ChatRoomStatus status) {
        return ChatRoom.restore(ROOM_ID, 1L, "서울시청", "강남역", NOW, status);
    }

    private SendMessageCommand command() {
        return new SendMessageCommand(ROOM_ID, USER_ID, "안녕");
    }

    private FindMessageCommand findMessageCommand(Long cursor, int size) {
        return new FindMessageCommand(USER_ID, ROOM_ID, cursor, size);
    }

    private SearchMessageCommand searchMessageCommand(String keyword, Long cursor, int size) {
        return new SearchMessageCommand(USER_ID, ROOM_ID, keyword, cursor, size);
    }

    @Test
    void size보다_많이_조회하면_hasNext_true() {
        given(chatMessageRepository.findBefore(ROOM_ID, null, 3)) // Repo에서는 한개 더 불러와서 다음이 있는지 없는지 판단을 함
                .willReturn(List.of(message(3L), message(2L), message(1L)));

        ChatMessageSlice slice = chatMessageService.findBefore(findMessageCommand(null, 2));

        assertThat(slice.hasNext()).isTrue();
        assertThat(slice.messages()).hasSize(2);
        assertThat(slice.nextCursor()).isEqualTo(2L);
    }

    @Test
    void size보다_같거나_적게_조회하면_hasNext_false() {
        given(chatMessageRepository.findBefore(ROOM_ID, null, 3))
                .willReturn(List.of(message(2L), message(1L)));

        ChatMessageSlice slice = chatMessageService.findBefore(findMessageCommand(null, 2));

        assertThat(slice.hasNext()).isFalse();
        assertThat(slice.messages()).hasSize(2);
        assertThat(slice.nextCursor()).isEqualTo(1L);
    }

    @Test
    void 채팅_메시지_저장_성공() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ChatRoomStatus.ACTIVE)));
        given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(message(1L));

        ChatMessageResult result = chatMessageService.sendMessage(command());

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.chatRoomId()).isEqualTo(ROOM_ID);

        verify(eventPublisher).publishEvent(new ChatMessageSentEvent(result));
    }

    @Test
    void 없는_방에_메시지_보내면_예외() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.sendMessage(command()))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 종료된_방에_메시지_보내면_예외() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ChatRoomStatus.CLOSED)));

        assertThatThrownBy(() -> chatMessageService.sendMessage(command()))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_CLOSED);
    }

    @Test
    void 참여자가_아니면_예외() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ChatRoomStatus.ACTIVE)));
        willThrow(new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                .given(chatRoomUserService).validateParticipant(USER_ID, ROOM_ID);

        assertThatThrownBy(() -> chatMessageService.sendMessage(command()))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT);
    }

    @Test
    void 참여자가_아니면_이력_조회_예외() {
        willThrow(new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                .given(chatRoomUserService).validateParticipant(USER_ID, ROOM_ID);

        assertThatThrownBy(() -> chatMessageService.findBefore(findMessageCommand(null, 30)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT);
    }

    @Test
    void size가_범위를_벗어나면_예외() {
        assertThatThrownBy(() -> chatMessageService.findBefore(findMessageCommand(null, 0)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_INVALID_PAGE_SIZE);
    }

    @Test
    void size가_최대를_넘어가면_예외() {
        assertThatThrownBy(() -> chatMessageService.findBefore(findMessageCommand(null, 101)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_INVALID_PAGE_SIZE);
    }

    @Test
    void cursor가_음수면_예외() {
        assertThatThrownBy(() -> chatMessageService.findBefore(findMessageCommand(-1L, 20)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_INVALID_CURSOR);
    }

    @Test
    void findAfter_size보다_많이_조회시_hasNext_true() {
        given(chatMessageRepository.findAfter(ROOM_ID, 1L, 3))
                .willReturn(List.of(message(2L), message(3L), message(4L)));

        ChatMessageSlice slice = chatMessageService.findAfter(findMessageCommand(1L, 2));

        assertThat(slice.hasNext()).isTrue();
        assertThat(slice.messages()).hasSize(2);
        assertThat(slice.nextCursor()).isEqualTo(3L);
    }

    @Test
    void findAfter_cursor_없으면_예외() {
        assertThatThrownBy(() -> chatMessageService.findAfter(findMessageCommand(null, 2)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_INVALID_CURSOR);
    }

    @Test
    void 메시지_삭제_성공() {
        ChatMessage message = message(1L);
        given(chatMessageRepository.findById(1L)).willReturn(Optional.of(message));
        given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(message);

        chatMessageService.deleteMessage(ROOM_ID, 1L, USER_ID);

        assertThat(message.isDeleted()).isTrue();
        verify(eventPublisher).publishEvent(any(ChatMessageDeleteEvent.class));
    }

    @Test
    void 메시지_삭제시_본인_메시지_아니면_예외() {
        given(chatMessageRepository.findById(1L)).willReturn(Optional.of(message(1L)));

        assertThatThrownBy(() -> chatMessageService.deleteMessage(ROOM_ID, 1L, 99L))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_MESSAGE_OWNER);
    }

    @Test
    void 없는_메시지_삭제시_예외() {
        given(chatMessageRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.deleteMessage(ROOM_ID, 1L, USER_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND);
    }

    @Test
    void 검색_결과가_size보다_많으면_hasNext_true() {
        given(chatMessageRepository.search(ROOM_ID, "안녕", null, 3))
                .willReturn(List.of(message(3L), message(2L), message(1L)));

        ChatMessageSlice slice = chatMessageService.searchMessage(searchMessageCommand("안녕", null, 2));

        assertThat(slice.hasNext()).isTrue();
        assertThat(slice.messages()).hasSize(2);
        assertThat(slice.nextCursor()).isEqualTo(2L);
    }

    @Test
    void 검색어가_짧으면_예외() {
        assertThatThrownBy(() -> chatMessageService.searchMessage(searchMessageCommand("안", null, 30)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_INVALID_KEYWORD);
    }

    @Test
    void 검색어가_공백뿐이면_예외() {
        assertThatThrownBy(() -> chatMessageService.searchMessage(searchMessageCommand("   ", null, 30)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_INVALID_KEYWORD);
    }

    @Test
    void 참여자가_아니면_검색_예외() {
        willThrow(new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                .given(chatRoomUserService).validateParticipant(USER_ID, ROOM_ID);

        assertThatThrownBy(() -> chatMessageService.searchMessage(searchMessageCommand("안녕", null, 30)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT);
    }
}
