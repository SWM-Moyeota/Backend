package team.codingforest.moyeota.chat.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRoomUserTest {
    private static final Long USER_ID = 7L;
    private static final Long ROOM_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");

    @Test
    void 참여시_초기상태() {
        ChatRoomUser user = ChatRoomUser.join(USER_ID, ROOM_ID, NOW);

        assertThat(user.getUserId()).isEqualTo(USER_ID);
        assertThat(user.getChatRoomId()).isEqualTo(ROOM_ID);
        assertThat(user.getJoinedAt()).isEqualTo(NOW);
        assertThat(user.getLastReadMessageId()).isNull();
        assertThat(user.isNotificationMuted()).isFalse();
        assertThat(user.getLeftAt()).isNull();
        assertThat(user.hasLeft()).isFalse();
    }

    @Test
    void 나가면_시각이_기록되고_hasLeft_true() {
        ChatRoomUser user = ChatRoomUser.join(USER_ID, ROOM_ID, NOW);
        Instant leftAt = NOW.plusSeconds(60);

        user.leave(leftAt);

        assertThat(user.getLeftAt()).isEqualTo(leftAt);
        assertThat(user.hasLeft()).isTrue();
    }

    @Test
    void 이미_나간_유저가_다시_나가면_예외() {
        ChatRoomUser user = ChatRoomUser.join(USER_ID, ROOM_ID, NOW);
        user.leave(NOW.plusSeconds(60));

        assertThatThrownBy(() -> user.leave(NOW.plusSeconds(120)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT);
    }

    @Test
    void 읽음처리시_lastReadMessageId_갱신() {
        ChatRoomUser user = ChatRoomUser.join(USER_ID, ROOM_ID, NOW);

        user.read(10L);

        assertThat(user.getLastReadMessageId()).isEqualTo(10L);
    }

    @Test
    void 더_큰_messageId면_갱신() {
        ChatRoomUser user = ChatRoomUser.join(USER_ID, ROOM_ID, NOW);
        user.read(10L);

        user.read(20L);

        assertThat(user.getLastReadMessageId()).isEqualTo(20L);
    }

    @Test
    void 더_작은_messageId면_갱신안함() {
        ChatRoomUser user = ChatRoomUser.join(USER_ID, ROOM_ID, NOW);
        user.read(20L);

        user.read(10L);

        assertThat(user.getLastReadMessageId()).isEqualTo(20L);
    }

    @Test
    void messageId가_null이면_무시() {
        ChatRoomUser user = ChatRoomUser.join(USER_ID, ROOM_ID, NOW);
        user.read(10L);

        user.read(null);

        assertThat(user.getLastReadMessageId()).isEqualTo(10L);
    }

    @Test
    void 알림_음소거_및_해제() {
        ChatRoomUser user = ChatRoomUser.join(USER_ID, ROOM_ID, NOW);

        user.muteNotification();
        assertThat(user.isNotificationMuted()).isTrue();

        user.unmuteNotification();
        assertThat(user.isNotificationMuted()).isFalse();
    }
}
