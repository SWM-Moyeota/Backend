package team.codingforest.moyeota.chat.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.chat.app.dto.ChatMessageResult;
import team.codingforest.moyeota.chat.app.dto.ChatMessageSlice;
import team.codingforest.moyeota.chat.app.dto.SendMessageCommand;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatMessageRepository;
import team.codingforest.moyeota.chat.domain.ChatMessageType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {
    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @InjectMocks
    private ChatMessageService chatMessageService;

    private ChatMessage message(Long id) {
        return ChatMessage.restore(id, ROOM_ID, USER_ID, "내용", ChatMessageType.TEXT, null, NOW, null);
    }

    @Test
    void size보다_많이_조회하면_hasNext_true() {
        given(chatMessageRepository.findBefore(ROOM_ID, null, 3)) // Repo에서는 한개 더 불러와서 다음이 있는지 없는지 판단을 함
                .willReturn(List.of(message(3L), message(2L), message(1L)));

        ChatMessageSlice slice = chatMessageService.findBefore(ROOM_ID, null, 2);

        assertThat(slice.hasNext()).isTrue();
        assertThat(slice.messages()).hasSize(2);
        assertThat(slice.nextCursor()).isEqualTo(2L);
    }

    @Test
    void size보다_같거나_적게_조회하면_hasNext_false() {
        given(chatMessageRepository.findBefore(ROOM_ID, null, 3))
            .willReturn(List.of(message(2L), message(1L)));

        ChatMessageSlice slice = chatMessageService.findBefore(ROOM_ID, null, 2);

        assertThat(slice.hasNext()).isFalse();
        assertThat(slice.messages()).hasSize(2);
        assertThat(slice.nextCursor()).isEqualTo(1L);
    }

    @Test
    void 채팅_메시지_저장_성공() {
        ChatMessage chatMessage = message(1L);
        given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(chatMessage);

        ChatMessageResult result = chatMessageService.sendMessage(new SendMessageCommand(ROOM_ID, USER_ID, "안녕"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.chatRoomId()).isEqualTo(ROOM_ID);
    }
}