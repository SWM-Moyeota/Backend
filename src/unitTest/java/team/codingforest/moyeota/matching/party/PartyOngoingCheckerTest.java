package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.Radius;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PartyOngoingCheckerTest {
    private static final Long 기사유저 = 100L;
    private static final Long 기사 = 9L;

    private PartyJpaTest parties;
    private Map<Long, Long> 유저의기사;
    private List<Long> 잠근유저;
    private PartyOngoingChecker checker;

    @BeforeEach
    void setUp() {
        parties = new PartyJpaTest();
        유저의기사 = new HashMap<>();
        유저의기사.put(기사유저, 기사);
        잠근유저 = new ArrayList<>();
        checker = new PartyOngoingChecker(parties, new PartyServiceTest.FakeDriverAccess() {
            @Override
            public Optional<Long> lockIdByUserId(Long userId) {
                잠근유저.add(userId);
                return Optional.ofNullable(유저의기사.get(userId));
            }
        });
    }

    private Party assignedParty() {
        Party party = Party.open(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(2), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
        party.join(2L);
        party.startMatching(Instant.now());
        party.assignDriver(기사);
        return party;
    }

    @Test
    void 기사로_배정된_합승이_진행_중이면_진행_중으로_본다() {
        parties.save(assignedParty());

        assertThat(checker.hasOngoingParty(기사유저)).isTrue();
    }

    @Test
    void 기사가_운행_중이면_진행_중으로_본다() {
        Party party = assignedParty();
        party.startRide(기사);
        parties.save(party);

        assertThat(checker.hasOngoingParty(기사유저)).isTrue();
    }

    @Test
    void 기사로_배정됐던_합승이_끝났으면_진행_중으로_보지_않는다() {
        Party party = assignedParty();
        party.startRide(기사);
        party.completeRide(기사, 12000);
        parties.save(party);

        assertThat(checker.hasOngoingParty(기사유저)).isFalse();
    }

    @Test
    void 기사가_아니고_합승도_없으면_진행_중으로_보지_않는다() {
        parties.save(assignedParty());

        assertThat(checker.hasOngoingParty(999L)).isFalse();
    }

    @Test
    void 멤버로_들어간_합승이_진행_중이면_진행_중으로_본다() {
        parties.save(assignedParty());

        assertThat(checker.hasOngoingParty(2L)).isTrue();
    }

    @Test
    void 멤버로_진행_중이어도_기사_행은_잠근다() {
        parties.save(assignedParty());

        assertThat(checker.hasOngoingParty(2L)).isTrue();
        assertThat(잠근유저).containsExactly(2L);
    }
}
