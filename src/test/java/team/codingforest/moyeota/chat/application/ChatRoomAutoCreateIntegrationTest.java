package team.codingforest.moyeota.chat.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import team.codingforest.moyeota.chat.domain.ChatRoomRepository;
import team.codingforest.moyeota.matching.application.PartyApplicationService;
import team.codingforest.moyeota.matching.domain.*;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  정원이 차서 MatchingStartedEvent 가 커밋 뒤 발행되면 채팅방이 "실제 DB 에" 남아야 한다.
 *  AFTER_COMMIT 리스너 안에서 @Transactional(REQUIRED) 를 부르면 이미 끝난 트랜잭션에 참여해 커밋이 안 되는 함정을 잡는 테스트.
 *  실제 DB(로컬 Postgres)로 돌아야 의미가 있어 @Transactional 을 붙이지 않는다.
 */
@SpringBootTest
class ChatRoomAutoCreateIntegrationTest {
    @Autowired PartyApplicationService partyService;
    @Autowired Parties parties;
    @Autowired ChatRoomRepository chatRoomRepository;

    @Test
    void 정원이_차면_커밋_후_채팅방이_DB에_남는다() {
        long base = 900_000L + System.currentTimeMillis() % 90_000L;   // 이전 실행의 진행 중 방과 겹치지 않게
        Party party = parties.save(Party.open(base, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(2), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC"));

        partyService.join(party.getId(), base + 1);   // 정원 2 → startMatching → AFTER_COMMIT 리스너

        assertThat(chatRoomRepository.findByPartyId(party.getId()))
                .as("리스너 로그엔 생성됐다고 찍혀도 커밋이 안 되면 여기서 비어 있다")
                .isPresent();
    }
}
