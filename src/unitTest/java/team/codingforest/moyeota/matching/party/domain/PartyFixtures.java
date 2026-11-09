package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  Party 도메인 테스트가 함께 쓰는 방·멤버 생성 도우미. 시각은 모두 NOW 기준으로 고정한다
 */
final class PartyFixtures {
    static final Long HOST_ID = 1L;
    static final Long MEMBER_ID = 2L;
    static final Long THIRD_ID = 3L;
    static final Long OUTSIDER_ID = 99L;
    static final Location DEPARTURE = new Location(37.4979, 127.0276);
    static final Location DESTINATION = new Location(37.3948, 127.1112);
    static final Location OTHER_EXIT = new Location(37.4985, 127.0290);
    static final String DEPARTURE_PLACE = "강남역";
    static final String DESTINATION_PLACE = "판교역";
    static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    static final Instant MEETING_AT = NOW.plus(Duration.ofMinutes(30));
    static final Instant RESERVED_AT = NOW.plus(Duration.ofDays(1));
    static final int FARE = 12000;
    static final int MINUTES = 25;
    static final String ROUTE = "encoded-path";

    private PartyFixtures() {}

    /** HOST_ID 가 NOW 에 만든 실시간 방에 others 가 차례로 들어온다 */
    static Party realtime(int capacity, Long... others) {
        Party party = Party.create(HOST_ID, DEPARTURE, DESTINATION, DEPARTURE_PLACE, DESTINATION_PLACE, new Capacity(capacity), NOW, FARE, MINUTES, ROUTE);
        join(party, others);
        return party;
    }

    /** HOST_ID 가 NOW 에 만든, RESERVED_AT 에 출발하는 예약 방 */
    static Party reserved(int capacity, Long... others) {
        Party party = Party.reserve(HOST_ID, DEPARTURE, DESTINATION, DEPARTURE_PLACE, DESTINATION_PLACE, new Capacity(capacity), NOW, RESERVED_AT);
        join(party, others);
        return party;
    }

    /** 방장이 NOW 에 MEETING_AT 로 약속을 확정한 실시간 방 */
    static Party confirmed(int capacity, Long... others) {
        Party party = realtime(capacity, others);
        party.confirm(HOST_ID, new MeetingPlan(MEETING_AT, null, null), NOW);
        return party;
    }

    /** 저장된 모양 그대로 복원한 방. members 의 첫 번째가 방장이다 */
    static Party restored(RoomType roomType, PartyStatus status, Instant departureAt, PartyMember... members) {
        boolean realtime = roomType == RoomType.REALTIME;
        return Party.load(100L, DEPARTURE, DESTINATION, DEPARTURE_PLACE, DESTINATION_PLACE, new Capacity(3),
                new ArrayList<>(List.of(members)), members.length == 0 ? null : members[0].getMemberId(), NOW.minus(Duration.ofHours(1)), status,
                realtime ? FARE : null, realtime ? MINUTES : null, realtime ? ROUTE : null,
                7L, roomType, departureAt);
    }

    /** order 가 작을수록 먼저 합류했다 */
    static PartyMember waiting(Long memberId, int order) {
        return PartyMember.load(memberId, NOW.minus(Duration.ofHours(1)).plusSeconds(order), null);
    }

    static PartyMember arrived(Long memberId, int order) {
        return PartyMember.load(memberId, NOW.minus(Duration.ofHours(1)).plusSeconds(order), MEETING_AT);
    }

    static void assertRejected(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    private static void join(Party party, Long... others) {
        int order = 1;
        for(Long other : others) {
            party.join(other, NOW.plusSeconds(order++));
        }
    }
}
