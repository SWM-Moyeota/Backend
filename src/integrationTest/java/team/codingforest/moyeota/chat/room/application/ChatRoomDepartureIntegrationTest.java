package team.codingforest.moyeota.chat.room.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;
import team.codingforest.moyeota.chat.room.infrastructure.ChatRooms;
import team.codingforest.moyeota.matching.party.PartyService;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingPlan;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 *  방장이 약속을 확정하며 만남 장소를 바꾸면 PartyConfirmedEvent 를 받아 채팅방 출발지도 바뀌어야 한다 (#334)
 *  리스너가 @ApplicationModuleListener(@Async) 라 커밋 직후엔 아직 안 돌았을 수 있어 await 로 기다린다
 */
@SpringBootTest
class ChatRoomDepartureIntegrationTest {
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location MEETING_PLACE = new Location(37.4981, 127.0279);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);

    @Autowired PartyService partyService;
    @Autowired Parties parties;
    @Autowired ChatRooms chatRooms;

    @Test
    void 약속을_확정하며_만남_장소를_바꾸면_채팅방_출발지도_바뀐다() {
        long host = 800_000L + System.currentTimeMillis() % 90_000L;
        long joiner = host + 1;

        Party party = parties.save(Party.create(host, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(2), Instant.now(),
                12000, 25, "_p~iF~ps|U_ulLnnqC"));
        Long partyId = party.getId();

        partyService.join(partyId, joiner);
        await().atMost(5, TimeUnit.SECONDS).until(() -> chatRooms.findByPartyId(partyId).isPresent());
        assertThat(chatRooms.findByPartyId(partyId).orElseThrow().getDeparturePlace()).isEqualTo("강남역");

        partyService.confirm(partyId, host, new MeetingPlan(Instant.now().plus(Duration.ofMinutes(10)), MEETING_PLACE, "강남역 11번 출구"));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ChatRoom chatRoom = chatRooms.findByPartyId(partyId).orElseThrow();
            assertThat(chatRoom.getDeparturePlace()).isEqualTo("강남역 11번 출구");
            assertThat(chatRoom.getDestinationPlace()).isEqualTo("판교역");
        });
    }
}
