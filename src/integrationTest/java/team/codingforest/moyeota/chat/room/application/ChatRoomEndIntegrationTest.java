package team.codingforest.moyeota.chat.room.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;
import team.codingforest.moyeota.chat.room.domain.ChatRoomStatus;
import team.codingforest.moyeota.chat.room.infrastructure.ChatRooms;
import team.codingforest.moyeota.matching.party.PartyService;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingPlan;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 *  합승이 끝나면 PartyClosedEvent 를 받아 채팅방이 FINISHED(같이 탐) 또는 CANCELED(같이 타지 못함)가 되어야 한다 (#335)
 *  리스너가 @ApplicationModuleListener(@Async) 라 커밋 직후엔 아직 안 돌았을 수 있어 await 로 기다린다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class ChatRoomEndIntegrationTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 대구 좌표를 쓴다
    private static final double DEPARTURE_LAT = 35.8714;
    private static final double DEPARTURE_LNG = 128.6014;
    private static final Location DEPARTURE = new Location(DEPARTURE_LAT, DEPARTURE_LNG);
    private static final Location DESTINATION = new Location(35.8880, 128.6110);

    @Autowired PartyService partyService;
    @Autowired Parties parties;
    @Autowired ChatRooms chatRooms;
    @Autowired JdbcTemplate jdbc;

    private final Long 방장 = ThreadLocalRandom.current().nextLong(5_000_000_000L, 6_000_000_000L);
    private final Long 동승자 = 방장 + 1;

    @Test
    void 방장이_출발해_합승이_끝나면_채팅방이_FINISHED_가_된다() {
        Long partyId = confirmedRoomWithChat();

        jdbc.update("UPDATE match_room SET departure_at = now() - interval '30 seconds' WHERE id = ?", partyId);
        partyService.arrive(partyId, 방장, DEPARTURE_LAT, DEPARTURE_LNG);
        partyService.arrive(partyId, 동승자, DEPARTURE_LAT, DEPARTURE_LNG);
        partyService.depart(partyId, 방장);

        awaitStatus(partyId, ChatRoomStatus.FINISHED);
    }

    @Test
    void 확정_뒤_동승자가_나가_합승이_취소되면_채팅방이_CANCELED_가_된다() {
        Long partyId = confirmedRoomWithChat();

        partyService.leave(partyId, 동승자);

        awaitStatus(partyId, ChatRoomStatus.CANCELED);
    }

    /**
     *  방장만 있는 방에 동승자를 참여시켜 채팅방을 만들고 약속을 확정한다.
     *  방장은 리포지토리로 직접 넣어 채팅방 생성이 참여 이벤트로만 일어나게 한다
     */
    private Long confirmedRoomWithChat() {
        Party party = parties.save(Party.create(방장, DEPARTURE, DESTINATION, "동대구역", "경북대", new Capacity(2), Instant.now(),
                8000, 15, "_p~iF~ps|U_ulLnnqC"));
        Long partyId = party.getId();

        partyService.join(partyId, 동승자);
        await().atMost(5, TimeUnit.SECONDS).until(() -> chatRooms.findByPartyId(partyId).isPresent());

        partyService.confirm(partyId, 방장, new MeetingPlan(Instant.now().plus(Duration.ofMinutes(10)), DEPARTURE, "동대구역"));
        return partyId;
    }

    private void awaitStatus(Long partyId, ChatRoomStatus expected) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ChatRoom chatRoom = chatRooms.findByPartyId(partyId).orElseThrow();
            assertThat(chatRoom.getStatus()).isEqualTo(expected);
            chatRoom.validateCanSend();   // 합승이 끝나도 대화는 계속 된다
        });
    }
}
