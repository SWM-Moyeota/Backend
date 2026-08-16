package team.codingforest.moyeota.chat.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRoomTest {
    private static final Long PARTY_ID = 100L;
    private static final String DEPARTURE = "서울시청";
    private static final String DESTINATION = "강남역";
    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
    private static final Instant DEPARTURE_TIME = NOW.plusSeconds(3600);

    private ChatRoom roomCreate(ChatRoomStatus status) {
        return ChatRoom.restore(1L, PARTY_ID, DEPARTURE, DESTINATION, NOW, status);
    }

    @Test
    void 방_생성시_ACTIVE() {
        ChatRoom chatRoom = ChatRoom.create(PARTY_ID, DEPARTURE, DESTINATION, NOW);

        assertThat(chatRoom.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
    }

    @Test
    void 방_종료시_CLOSED() {
        ChatRoom chatRoom = roomCreate(ChatRoomStatus.ACTIVE);
        chatRoom.close();
        assertThat(chatRoom.getStatus()).isEqualTo(ChatRoomStatus.CLOSED);
    }

    @Test
    void 종료된_방을_종료하면_예외() {
        ChatRoom chatRoom = roomCreate(ChatRoomStatus.CLOSED);

        assertThatThrownBy(chatRoom::close)
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_CLOSED);
    }

    @Test
    void 보관된_방을_종료하면_예외() {
        ChatRoom chatRoom = roomCreate(ChatRoomStatus.ARCHIVED);

        assertThatThrownBy(chatRoom::close)
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_CLOSED);
    }

    @Test
    void 종료된_방을_보관하면_ARCHIVE() {
        ChatRoom chatRoom = roomCreate(ChatRoomStatus.CLOSED);
        chatRoom.archive();
        assertThat(chatRoom.getStatus()).isEqualTo(ChatRoomStatus.ARCHIVED);
    }

    @Test
    void 보관된_방을_보관하면_예외() {
        ChatRoom chatRoom = roomCreate(ChatRoomStatus.ARCHIVED);

        assertThatThrownBy(() -> chatRoom.archive())
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_INVALID_STATUS);
    }

    @Test
    void 종료된_방에서_메시지_전송_불가() {
        ChatRoom chatRoom = roomCreate(ChatRoomStatus.CLOSED);

        assertThatThrownBy(chatRoom::validateCanSend)
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_CLOSED);
    }

    @Test
    void 보관된_방에서_메시지_전송_불가() {
        ChatRoom chatRoom = roomCreate(ChatRoomStatus.ARCHIVED);

        assertThatThrownBy(chatRoom::validateCanSend)
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_CLOSED);
    }
}