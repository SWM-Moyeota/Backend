package team.codingforest.moyeota.chat.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.domain.ChatRooms;
import team.codingforest.moyeota.matching.application.PartyApplicationService;
import team.codingforest.moyeota.matching.domain.Capacity;
import team.codingforest.moyeota.matching.domain.Location;
import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.Radius;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  정원이 차서 MatchingStartedEvent 가 커밋 뒤 발행되면 채팅방이 "실제 DB 에" 남아야 한다.
 *  AFTER_COMMIT 리스너 안에서 @Transactional(REQUIRED) 를 부르면 이미 끝난 트랜잭션에 참여해 커밋이 안 되는 함정을 잡는 테스트.
 *  실제 DB(로컬 Postgres)로 돌아야 의미가 있어 @Transactional 을 붙이지 않는다.
 */
@SpringBootTest
class ChatRoomAutoCreateIntegrationTest {
    static final org.apache.curator.test.TestingCluster cluster = startCluster();
    static org.apache.curator.test.TestingCluster startCluster() {
        System.setProperty("zookeeper.nio.numWorkerThreads", "4");
        System.setProperty("zookeeper.nio.numSelectorThreads", "1");
        var ensemble = new org.apache.curator.test.TestingCluster(3);
        try { ensemble.start(); return ensemble; }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    @org.springframework.test.context.DynamicPropertySource
    static void zookeeper(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("matching.lock.zookeeper.connect-string", cluster::getConnectString);
    }
    @org.junit.jupiter.api.AfterAll static void closeCluster() throws Exception { cluster.close(); }

    @Autowired PartyApplicationService partyService;
    @Autowired Parties parties;
    @Autowired team.codingforest.moyeota.user.domain.Users users;
    @Autowired
    ChatRooms chatRooms;
    @Autowired
    ChatRoomUsers chatRoomUsers;
    @Test
    void 파티원이_들어오면_커밋_후_채팅방과_참여가_DB에_남는다() {
        long base = 900_000L + System.currentTimeMillis() % 90_000L;
        long joiner = users.save(team.codingforest.moyeota.user.domain.User.from(java.util.UUID.randomUUID(),
                team.codingforest.moyeota.user.domain.enums.LoginType.LOCAL)).getId();

        // 방장은 리포지토리로 직접 넣어 이벤트를 태우지 않는다 - 채팅방 생성이 참여 이벤트만으로 일어나는지 본다
        Party party = parties.save(Party.open(base, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(2), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC"));

        partyService.join(party.getId(), joiner);

        ChatRoom chatRoom = chatRooms.findByPartyId(party.getId())
                .orElseThrow(() -> new AssertionError("리스너 로그엔 생성됐다고 찍혀도 커밋이 안 되면 여기서 비어 있다"));

        assertThat(chatRoomUsers.findAllByChatRoomId(chatRoom.getId()))
                .as("방만 만들어지고 참여가 커밋되지 않으면 비어 있다")
                .extracting(ChatRoomUser::getUserId)
                .containsExactly(joiner);
    }
}