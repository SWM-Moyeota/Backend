package team.codingforest.moyeota.chat.room.application;

import team.codingforest.moyeota.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.room.infrastructure.ChatRooms;
import team.codingforest.moyeota.matching.party.PartyService;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.Radius;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
/**
 * 참여/퇴장 이벤트가 뒤바뀐 순서로 처리돼도 채팅방 멤버십은 파티의 현재 멤버십과 같아야 한다 (#143).
 * 비동기 경합에 기대지 않고, 파티 퇴장까지 커밋한 뒤 서비스를 역순으로 직접 불러 결정적으로 재현한다.
 * 실제 DB(로컬 Postgres)로 돌아야 의미가 있어 @Transactional 을 붙이지 않는다.
 */
@IntegrationTest
@SpringBootTest
class ChatRoomSyncOrderIntegrationTest {
    @Autowired PartyService partyService;
    @Autowired Parties parties;
    @Autowired MatchingChatRoomService matchingChatRoomService;
    @Autowired ChatRooms chatRooms;
    @Autowired ChatRoomUsers chatRoomUsers;

    @Test
    void 퇴장이_참여보다_먼저_처리돼도_나간_사람은_채팅방에_남지_않는다() {
        long base = 800_000L + System.currentTimeMillis() % 90_000L;
        long leaver = base + 1;

        // 정원 3 - 방장 + 1명이면 모집 중이라 나갈 수 있다. 방장은 리포지토리로 직접 넣어 이벤트를 태우지 않는다
        Party party = parties.save(Party.open(base, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC"));

        // 1. 참여 → 리스너가 방을 만들고 넣을 때까지 기다린다
        partyService.join(party.getId(), leaver);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Long id = chatRooms.findByPartyId(party.getId()).map(ChatRoom::getId).orElseThrow();
            assertThat(chatRoomUsers.findActiveByUserIdAndChatRoomId(leaver, id)).isPresent();
        });
        Long roomId = chatRooms.findByPartyId(party.getId()).map(ChatRoom::getId).orElseThrow();

        // 2. 퇴장 → 리스너가 내보낼 때까지 기다린다
        partyService.leave(party.getId(), leaver);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(chatRoomUsers.findActiveByUserIdAndChatRoomId(leaver, roomId)).isEmpty());

        // 3. 역순 도착 재현: 퇴장 이벤트가 먼저 처리되고, 참여 이벤트가 뒤늦게 도착
        matchingChatRoomService.leaveMember(party.getId(), leaver);
        matchingChatRoomService.joinMember(party.getId(), leaver);

        assertThat(chatRoomUsers.findActiveByUserIdAndChatRoomId(leaver, roomId))
                .as("파티에서 나간 사람이 채팅방에 남으면 안 된다")
                .isEmpty();
    }
}