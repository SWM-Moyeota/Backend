package team.codingforest.moyeota.matching.party.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  방 상세(GET /matching/rooms/{partyId})와 SSE connected 가 함께 쓰는 응답 모양.
 *  옛 앱 호환용으로 남겼던 키(taxiDriverId·departureRadius·destinationRadius·members[].badgeId)는 새 앱과 함께 없앴다
 */
class PartyDetailResultTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @DisplayName("from - 정원이 찬 실시간 방도 ACTIVE 로 내보낸다")
    @Test
    void from_fullRealtime() {

        /*
         * test
         */
        PartyDetailResult result = PartyDetailResult.from(fullParty(), Map.of(), Map.of());

        /*
         * verify
         */
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.currentMembers()).isEqualTo(2);
        assertThat(result.roomType()).isEqualTo("REALTIME");
        assertThat(result.confirmed()).isFalse();
    }

    @DisplayName("from - 예약 방은 RESERVED 와 출발 시각을 내보내고 만들 때부터 약속이 있어 confirmed 다")
    @Test
    void from_reservation() {

        /*
         * test
         */
        PartyDetailResult result = PartyDetailResult.from(reservation(), Map.of(), Map.of());

        /*
         * verify
         */
        assertThat(result.roomType()).isEqualTo("RESERVED");
        assertThat(result.departureAt()).isEqualTo(CREATED_AT.plus(Duration.ofDays(1)));
        assertThat(result.confirmed()).isTrue();
    }

    @DisplayName("from - JSON 에 없앤 키(taxiDriverId·departureRadius·destinationRadius·members[].badgeId)가 없다")
    @Test
    void from_removedKeysAbsent() {

        /*
         * test
         */
        JsonNode realtime = jsonMapper.readTree(jsonMapper.writeValueAsString(PartyDetailResult.from(fullParty(), Map.of(), Map.of())));
        JsonNode reservation = jsonMapper.readTree(jsonMapper.writeValueAsString(PartyDetailResult.from(reservation(), Map.of(), Map.of())));

        /*
         * verify
         */
        for (JsonNode json : List.of(realtime, reservation)) {
            assertThat(json.has("taxiDriverId")).isFalse();
            assertThat(json.has("departureRadius")).isFalse();
            assertThat(json.has("destinationRadius")).isFalse();
            assertThat(json.get("members").get(0).has("badgeId")).isFalse();
        }
    }

    /** 정원이 찬 실시간 방 - 정원이 차도 ACTIVE 다 */
    private static Party fullParty() {
        List<PartyMember> members = List.of(PartyMember.load(1L, CREATED_AT, null), PartyMember.load(2L, CREATED_AT.plusSeconds(1), null));

        return Party.load(100L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), "강남역", "판교역",
                new Capacity(2), members, 1L, CREATED_AT, PartyStatus.ACTIVE, 12000, 25, "encoded-path", 0L,
                RoomType.REALTIME, null);
    }

    /** 하루 뒤에 출발하는 혼자인 예약 방 */
    private static Party reservation() {
        return Party.load(200L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), "강남역", "판교역",
                new Capacity(3), List.of(PartyMember.load(1L, CREATED_AT, null)), 1L, CREATED_AT, PartyStatus.ACTIVE, null, null, null, 0L,
                RoomType.RESERVED, CREATED_AT.plus(Duration.ofDays(1)));
    }
}
