package team.codingforest.moyeota.chat.infrastructure;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatRoomUser;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({ChatMessageJpa.class, ChatRoomUserJpa.class})
class ChatMessageUnreadCountJpaTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ME = 7L;
    private static final Long OTHER = 8L;
    private static final Long ANOTHER = 9L;
    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    private final ChatMessageJpa chatMessages;
    private final ChatRoomUserJpa chatRoomUsers;
    private final EntityManager em;

    @Autowired
    ChatMessageUnreadCountJpaTest(ChatMessageJpa chatMessages, ChatRoomUserJpa chatRoomUsers, EntityManager em) {
        this.chatMessages = chatMessages;
        this.chatRoomUsers = chatRoomUsers;
        this.em = em;
    }

    private void reload() {
        em.flush();
        em.clear();
    }

    private ChatMessage send(Long senderId, String content) {
        return chatMessages.save(ChatMessage.text(ROOM_ID, senderId, content, NOW));
    }

    private ChatRoomUser join(Long userId) {
        return chatRoomUsers.save(ChatRoomUser.join(userId, ROOM_ID, NOW));
    }

    @Test
    void 읽음_기록이_없으면_상대_메시지_전부가_안읽음() {
        join(ME);
        join(OTHER);
        send(OTHER, "안녕");
        send(OTHER, "뭐해");
        reload();

        Map<Long, Long> counts = chatMessages.countUnreadByUserId(ME);

        assertThat(counts).containsEntry(ROOM_ID, 2L);
    }

    @Test
    void 읽은_이후_메시지만_센다() {
        ChatRoomUser me = join(ME);
        join(OTHER);
        ChatMessage first = send(OTHER, "안녕");
        send(OTHER, "뭐해");
        send(OTHER, "자니");

        me.read(first.getId(), NOW);
        chatRoomUsers.save(me);
        reload();

        Map<Long, Long> counts = chatMessages.countUnreadByUserId(ME);

        assertThat(counts).containsEntry(ROOM_ID, 2L);
    }

    @Test
    void 내가_보낸_메시지는_세지_않는다() {
        join(ME);
        join(OTHER);
        send(ME, "내 메시지");
        send(ME, "내 메시지2");
        send(OTHER, "상대 메시지");
        reload();

        Map<Long, Long> counts = chatMessages.countUnreadByUserId(ME);

        assertThat(counts).containsEntry(ROOM_ID, 1L);
    }

    @Test
    void 삭제된_메시지는_세지_않는다() {
        join(ME);
        join(OTHER);
        ChatMessage deleted = send(OTHER, "지울 메시지");
        send(OTHER, "남을 메시지");

        deleted.delete(NOW);
        chatMessages.save(deleted);
        reload();

        Map<Long, Long> counts = chatMessages.countUnreadByUserId(ME);

        assertThat(counts).containsEntry(ROOM_ID, 1L);
    }

    @Test
    void 나간_방은_결과에_없다() {
        ChatRoomUser me = join(ME);
        join(OTHER);
        send(OTHER, "안녕");

        me.leave(NOW);
        chatRoomUsers.save(me);
        reload();

        Map<Long, Long> counts = chatMessages.countUnreadByUserId(ME);

        assertThat(counts).doesNotContainKey(ROOM_ID);
    }

    @Test
    void 참여자가_여럿이어도_내_읽음_기준으로만_센다() {
        join(ME);
        join(OTHER);
        join(ANOTHER);
        send(OTHER, "안녕");
        send(ANOTHER, "하이");
        reload();

        Map<Long, Long> counts = chatMessages.countUnreadByUserId(ME);

            // 참여자 3명과 조인되며 3배로 부풀지 않아야 한다
        assertThat(counts).containsEntry(ROOM_ID, 2L);
    }

    @Test
    void 안읽은_메시지가_없는_방은_결과에_없다() {
        ChatRoomUser me = join(ME);
        join(OTHER);
        ChatMessage last = send(OTHER, "안녕");

        me.read(last.getId(), NOW);
        chatRoomUsers.save(me);
        reload();

        Map<Long, Long> counts = chatMessages.countUnreadByUserId(ME);

        assertThat(counts).doesNotContainKey(ROOM_ID);
    }
}