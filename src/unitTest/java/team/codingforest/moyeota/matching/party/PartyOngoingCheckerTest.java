package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PartyOngoingCheckerTest {
    private PartyJpaTest parties;
    private PartyOngoingChecker checker;

    @BeforeEach
    void setUp() {
        parties = new PartyJpaTest();
        checker = new PartyOngoingChecker(parties);
    }

    /** 정원 2명이 찬 방(COMPLETED) */
    private Party fullParty() {
        Party party = Party.open(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(2), Instant.now(),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
        party.join(2L);
        return party;
    }

    @Test
    void 들어간_동승이_없으면_진행_중으로_보지_않는다() {
        parties.save(fullParty());

        assertThat(checker.hasOngoingParty(999L)).isFalse();
    }

    @Test
    void 멤버로_들어간_동승이_진행_중이면_진행_중으로_본다() {
        parties.save(fullParty());

        assertThat(checker.hasOngoingParty(2L)).isTrue();
    }
}
