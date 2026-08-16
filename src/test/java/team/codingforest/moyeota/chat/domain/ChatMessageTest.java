package team.codingforest.moyeota.chat.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageTest {
    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final String CONTENT = "안녕하세요";
    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");

    @Test
    void 텍스트메시지_ACTIVE_상태로_생성() {
        ChatMessage message = ChatMessage.text(ROOM_ID, USER_ID, CONTENT, NOW);

        assertThat(message.getType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(message.getStatus()).isEqualTo(ChatMessageStatus.ACTIVE);
        assertThat(message.isDeleted()).isFalse();
        assertThat(message.getDeletedAt()).isNull();
    }

    @Test
    void 위치메시지_LOCATION_타입으로_생성() {
        ChatMessage message = ChatMessage.location(ROOM_ID, USER_ID, CONTENT, NOW);

        assertThat(message.getType()).isEqualTo(ChatMessageType.LOCATION);
        assertThat(message.getStatus()).isEqualTo(ChatMessageStatus.ACTIVE);
    }

    @Test
    void 신규메시지_id가_없음() {
        ChatMessage message = ChatMessage.text(ROOM_ID, USER_ID, CONTENT, NOW);

        assertThat(message.getId()).isNull();
    }

    @Test
    void 삭제시_상태와_시각이_기록() {
        ChatMessage message = ChatMessage.text(ROOM_ID, USER_ID, CONTENT, NOW);
        Instant deletedAt = NOW.plusSeconds(60);

        message.delete(deletedAt);

        assertThat(message.isDeleted()).isTrue();
        assertThat(message.getStatus()).isEqualTo(ChatMessageStatus.DELETED);
        assertThat(message.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void 이미_삭제된_메시지를_재삭제시_예외() {
        ChatMessage message = ChatMessage.text(ROOM_ID, USER_ID, CONTENT, NOW);
        message.delete(NOW.plusSeconds(60));

        assertThatThrownBy(() -> message.delete(NOW.plusSeconds(120)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_MESSAGE_ALREADY_DELETED);
    }

    @Test
    void 삭제_시각이_생성_시각보다_빠르면_예외() {
        ChatMessage message = ChatMessage.text(ROOM_ID, USER_ID, CONTENT, NOW);

        assertThatThrownBy(() -> message.delete(NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restore하면_모든_필드_복원() {
        Instant deletedAt = NOW.plusSeconds(60);

        ChatMessage message = ChatMessage.restore(
                42L, ROOM_ID, USER_ID, CONTENT,
                ChatMessageType.TEXT, ChatMessageStatus.DELETED, NOW, deletedAt);

        assertThat(message.getId()).isEqualTo(42L);
        assertThat(message.getChatRoomId()).isEqualTo(ROOM_ID);
        assertThat(message.getUserId()).isEqualTo(USER_ID);
        assertThat(message.getContent()).isEqualTo(CONTENT);
        assertThat(message.getType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(message.getStatus()).isEqualTo(ChatMessageStatus.DELETED);
        assertThat(message.getCreatedAt()).isEqualTo(NOW);
        assertThat(message.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(message.isDeleted()).isTrue();
    }
}