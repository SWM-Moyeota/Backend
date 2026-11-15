package team.codingforest.moyeota.chat.room.infrastructure;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ChatRoomJpa.class)
class ChatRoomMessageSeqJpaTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    private final ChatRoomJpa chatRooms;
    private final EntityManager em;

    @Autowired
    ChatRoomMessageSeqJpaTest(ChatRoomJpa chatRooms, EntityManager em) {
        this.chatRooms = chatRooms;
        this.em = em;
    }

    private Long newRoom(Long partyId) {
        return chatRooms.save(ChatRoom.create(partyId, "강남역", "판교역", NOW)).getId();
    }

    @Test
    void 방마다_1부터_1씩_증가한다() {
        Long roomA = newRoom(1L);
        Long roomB = newRoom(2L);

        assertThat(chatRooms.nextMessageSeq(roomA)).contains(1L);
        assertThat(chatRooms.nextMessageSeq(roomA)).contains(2L);
        assertThat(chatRooms.nextMessageSeq(roomB)).contains(1L);
        assertThat(chatRooms.nextMessageSeq(roomA)).contains(3L);
    }

    @Test
    void 출발지를_바꿔도_순번과_다른_방은_그대로다() {
        Long roomId = newRoom(1L);
        Long otherRoomId = newRoom(2L);
        chatRooms.nextMessageSeq(roomId);
        Instant later = NOW.plusSeconds(60);

        int changed = chatRooms.changeDeparture(1L, "강남역 11번 출구", later);
        em.flush();
        em.clear();

        assertThat(changed).isEqualTo(1);
        ChatRoom room = chatRooms.findById(roomId).orElseThrow();
        assertThat(room.getDeparturePlace()).isEqualTo("강남역 11번 출구");
        assertThat(room.getDestinationPlace()).isEqualTo("판교역");
        assertThat(room.getUpdatedAt()).isEqualTo(later);
        assertThat(chatRooms.findById(otherRoomId).orElseThrow().getDeparturePlace()).isEqualTo("강남역");
        assertThat(chatRooms.nextMessageSeq(roomId)).contains(2L);
    }

    @Test
    void 방이_없는_파티의_출발지를_바꾸면_0을_돌려준다() {
        assertThat(chatRooms.changeDeparture(99L, "강남역 11번 출구", NOW)).isZero();
    }

    @Test
    void 방을_통째로_저장해도_순번을_옛값으로_덮어쓰지_않는다() {
        Long roomId = newRoom(1L);
        ChatRoom loadedBeforeMessages = chatRooms.findById(roomId).orElseThrow();   // 순번 0 일 때 읽어 둔 방

        chatRooms.nextMessageSeq(roomId);
        chatRooms.nextMessageSeq(roomId);

        loadedBeforeMessages.close(NOW);
        chatRooms.save(loadedBeforeMessages);   // ChatRoomService.close 와 같은 저장
        em.flush();
        em.clear();

        assertThat(chatRooms.nextMessageSeq(roomId)).contains(3L);
    }

    @Test
    void 조회한_방에는_그_시점의_최신_순번이_담긴다() {
        Long roomId = newRoom(1L);
        chatRooms.nextMessageSeq(roomId);
        chatRooms.nextMessageSeq(roomId);
        em.clear();

        assertThat(chatRooms.findById(roomId).orElseThrow().getLastSeq()).isEqualTo(2L);
    }

    @Test
    void 없는_방이면_빈_값() {
        assertThat(chatRooms.nextMessageSeq(999L)).isEmpty();
    }
}
