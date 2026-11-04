package team.codingforest.moyeota.matching.party.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.Radius;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  방 상세(GET /matching/rooms/{partyId})와 SSE connected 가 함께 쓰는 응답 모양.
 *  기사 기능을 없앴지만 출시된 앱이 taxiDriverId 를 읽으므로, 방 도메인에 기사 번호가 없어도 키는 null 로 남아야 한다
 */
class PartyDetailResultTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @DisplayName("from - 정원이 찬 방이어도 taxiDriverId 는 null 이다")
    @Test
    void from_taxiDriverIdIsNull() {

        /*
         * settings
         */
        Party party = completedParty();

        /*
         * test
         */
        PartyDetailResult result = PartyDetailResult.from(party, Map.of(), Map.of());

        /*
         * verify
         */
        assertThat(result.taxiDriverId()).isNull();
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @DisplayName("from - JSON 으로 내보내면 taxiDriverId 키는 남고 값은 null 이다")
    @Test
    void from_taxiDriverIdKeyKeptAsNull() {

        /*
         * settings
         */
        PartyDetailResult result = PartyDetailResult.from(completedParty(), Map.of(), Map.of());

        /*
         * test
         */
        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(result));

        /*
         * verify
         */
        assertThat(json.has("taxiDriverId")).as("키가 빠지면 출시된 앱의 응답 해석이 달라진다").isTrue();
        assertThat(json.get("taxiDriverId").isNull()).isTrue();
    }

    /** 정원이 차 COMPLETED 에 머문 실시간 방 - 예전에는 이 상태에서 기사가 배정됐다 */
    private static Party completedParty() {
        List<PartyMember> members = List.of(PartyMember.load(1L, CREATED_AT), PartyMember.load(2L, CREATED_AT.plusSeconds(1)));

        return Party.load(100L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역",
                new Capacity(2), members, CREATED_AT, PartyStatus.COMPLETED, 12000, 25, "encoded-path", null, 0L);
    }
}
