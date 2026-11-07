package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartyPlaces;
import team.codingforest.moyeota.matching.api.dto.PartyRiders;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 *  다른 모듈(택시비 정산)이 창구(PartyAccess)로 읽는 방 멤버·탑승자의 합류 순과 탑승자 규칙, 방 이름 일괄 조회를 실제 Postgres 에서 본다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class PartyAccessPostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 부산 좌표를 쓴다
    private static final Location DEPARTURE = new Location(35.1151, 129.0415);
    private static final Location DESTINATION = new Location(35.1587, 129.1604);

    @Autowired PartyAccess partyAccess;
    @Autowired Parties parties;
    @Autowired JdbcTemplate jdbc;

    private final Long 첫째 = ThreadLocalRandom.current().nextLong(8_000_000_000L, 9_000_000_000L);
    private final Long 둘째 = 첫째 + 1;
    private final Long 셋째 = 첫째 + 2;
    private final List<Long> rooms = new ArrayList<>();

    @AfterEach
    void tearDown() {
        rooms.forEach(id -> {
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    @Test
    void 멤버와_탑승자는_user_id_순이_아니라_합류_순이고_노쇼와_도착하지_않은_무효_노쇼는_탑승자가_아니다() {
        Instant meetingAt = meetingAt();
        // userId 순(첫째 < 둘째 < 셋째)과 다르게 셋째 → 첫째 → 둘째 순으로 들어왔다
        Long room = room(PartyStatus.FINISHED, "부산역", "해운대",
                PartyMember.load(셋째, meetingAt, meetingAt, null, null),
                PartyMember.load(첫째, meetingAt, null, meetingAt, null),
                PartyMember.load(둘째, meetingAt, meetingAt, null, null));

        PartyRiders riders = partyAccess.findRiders(room).orElseThrow();

        assertThat(riders.partyId()).isEqualTo(room);
        assertThat(riders.finished()).isTrue();
        assertThat(riders.memberIds()).containsExactly(셋째, 첫째, 둘째);
        assertThat(riders.riderIds()).as("노쇼(첫째)는 빠진다").containsExactly(셋째, 둘째);

        jdbc.update("UPDATE user_match_room SET no_show_voided_at = now() WHERE match_id = ? AND user_id = ?", room, 첫째);
        assertThat(partyAccess.findRiders(room).orElseThrow().riderIds()).as("무효로 돌아가도 도착하지 않았으면 탑승자가 아니다").containsExactly(셋째, 둘째);
    }

    @Test
    void 합류_시각이_같으면_user_id_순이다() {
        Instant meetingAt = meetingAt();
        Long room = room(PartyStatus.FINISHED, "부산역", "해운대",
                PartyMember.load(둘째, meetingAt, meetingAt, null, null),
                PartyMember.load(첫째, meetingAt, meetingAt, null, null));
        jdbc.update("UPDATE user_match_room SET created_at = ? WHERE match_id = ?", java.sql.Timestamp.from(meetingAt.minusSeconds(60)), room);

        PartyRiders riders = partyAccess.findRiders(room).orElseThrow();

        assertThat(riders.memberIds()).containsExactly(첫째, 둘째);
        assertThat(riders.riderIds()).containsExactly(첫째, 둘째);
    }

    @Test
    void 끝나지_않은_방은_finished_가_아니고_없는_방은_비어_있다() {
        Instant meetingAt = meetingAt();
        Long room = room(PartyStatus.COMPLETED, "부산역", "해운대",
                PartyMember.load(첫째, meetingAt, meetingAt, null, null),
                PartyMember.load(둘째, meetingAt, null, null, null));

        PartyRiders riders = partyAccess.findRiders(room).orElseThrow();

        assertThat(riders.finished()).isFalse();
        assertThat(riders.memberIds()).containsExactly(첫째, 둘째);
        assertThat(riders.riderIds()).containsExactly(첫째);
        assertThat(partyAccess.findRiders(-1L)).isEmpty();
    }

    @Test
    void 방별_출발지와_도착지_이름을_한_번에_읽고_없는_방은_빠진다() {
        Instant meetingAt = meetingAt();
        Long 해운대방 = room(PartyStatus.FINISHED, "부산역", "해운대", PartyMember.load(첫째, meetingAt, meetingAt, null, null));
        Long 서면방 = room(PartyStatus.ACTIVE, "센텀시티", "서면", PartyMember.load(둘째, meetingAt, null, null, null));

        assertThat(partyAccess.findPlaces(List.of(해운대방, 서면방, -1L)))
                .containsOnly(entry(해운대방, new PartyPlaces("부산역", "해운대")), entry(서면방, new PartyPlaces("센텀시티", "서면")));
        assertThat(partyAccess.findPlaces(List.of())).isEmpty();
    }

    private static Instant meetingAt() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofHours(1));
    }

    /** 넘긴 순서대로 합류한 방. 합류 시각(created_at)도 그 순서로 박는다 */
    private Long room(PartyStatus status, String departure, String destination, PartyMember... membersInJoinOrder) {
        Instant meetingAt = membersInJoinOrder[0].getJoinedAt();
        Long id = parties.save(Party.load(null, DEPARTURE, DESTINATION, departure, destination, new Capacity(3), new ArrayList<>(List.of(membersInJoinOrder)),
                membersInJoinOrder[0].getMemberId(), meetingAt.minus(Duration.ofHours(1)), status,
                12000, 25, "encoded-path", 0L, RoomType.REALTIME, meetingAt, null)).getId();
        rooms.add(id);

        for(int i = 0; i < membersInJoinOrder.length; i++) {
            jdbc.update("UPDATE user_match_room SET created_at = ? WHERE match_id = ? AND user_id = ?",
                    java.sql.Timestamp.from(meetingAt.minusSeconds(60 - i * 10L)), id, membersInJoinOrder[i].getMemberId());
        }

        return id;
    }
}
