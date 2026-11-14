package team.codingforest.moyeota.chat.message.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.chat.common.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.common.exception.ChatException;
import team.codingforest.moyeota.chat.message.domain.ChatMessage;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(ChatMessageJpa.class)
class ChatMessageClientMessageIdJpaTest {

    private static final Long ROOM_ID = 10L;
    private static final Long USER_ID = 7L;
    private static final Long OTHER_USER_ID = 8L;
    private static final UUID KEY = UUID.fromString("6f1c2b9e-3d4a-4e7b-9c1d-2a3b4c5d6e7f");
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    private final ChatMessageJpa chatMessages;
    private final ChatMessageJpaRepository jpaRepository;

    @Autowired
    ChatMessageClientMessageIdJpaTest(ChatMessageJpa chatMessages, ChatMessageJpaRepository jpaRepository) {
        this.chatMessages = chatMessages;
        this.jpaRepository = jpaRepository;
    }

    @Test
    void 사용자와_키로_저장한_메시지를_찾는다() {
        chatMessages.save(ChatMessage.text(ROOM_ID, 1L, USER_ID, KEY, "지금 출발", NOW));

        assertThat(chatMessages.findByUserIdAndClientMessageId(USER_ID, KEY))
                .hasValueSatisfying(found -> assertThat(found.getSeq()).isEqualTo(1L));
        assertThat(chatMessages.findByUserIdAndClientMessageId(OTHER_USER_ID, KEY)).isEmpty();
    }

    @Test
    void 같은_사용자가_같은_키로_두_번_저장하면_처리_중_예외로_바꾼다() {
        chatMessages.save(ChatMessage.text(ROOM_ID, 1L, USER_ID, KEY, "지금 출발", NOW));

        assertThatThrownBy(() -> chatMessages.save(ChatMessage.text(ROOM_ID, 2L, USER_ID, KEY, "지금 출발", NOW)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_MESSAGE_SENDING);
    }

    @Test
    void 키가_있어도_방_순번_중복은_원래_예외_그대로_던진다() {
        chatMessages.save(ChatMessage.text(ROOM_ID, 1L, USER_ID, KEY, "지금 출발", NOW));

        assertThatThrownBy(() -> chatMessages.save(ChatMessage.text(ROOM_ID, 1L, OTHER_USER_ID, UUID.randomUUID(), "같은 순번", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 키가_없는_메시지는_몇_개든_저장된다() {
        chatMessages.save(ChatMessage.text(ROOM_ID, 1L, USER_ID, "옛 앱 1", NOW));
        chatMessages.save(ChatMessage.text(ROOM_ID, 2L, USER_ID, "옛 앱 2", NOW));
        jpaRepository.flush();

        assertThat(jpaRepository.count()).isEqualTo(2);
    }
}
