package team.codingforest.moyeota.matching.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.PartyJpaTest;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.matching.sse.infrastructure.PartySseRegistry;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartySseServiceTest {
    private static final Long host = 1L;
    private static final Long participant = 2L;
    private static final Long guest = 4L;

    private PartyJpaTest parties;
    private PartySseRegistry registry;
    private PartySseService service;

    @BeforeEach
    void setUp() {
        parties = new PartyJpaTest();
        registry = new PartySseRegistry();
        service = new PartySseService(parties, registry);
    }

    @Test
    void 멤버는_방_변화를_구독할_수_있다() {
        Party party = parties.save(openParty(host));
        party.join(participant);
        parties.save(party);

        assertThat(service.subscribe(party.getId(), participant)).isNotNull();
        assertThat(registry.connections()).isEqualTo(1);
    }

    @Test
    void 멤버가_아니면_구독할_수_없다() {
        Party party = parties.save(openParty(host));

        assertThatThrownBy(() -> service.subscribe(party.getId(), guest))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_PARTY_MEMBER);
        assertThat(registry.connections()).as("거절된 요청은 연결을 남기지 않는다").isZero();
    }

    @Test
    void 없는_방은_구독할_수_없다() {
        assertThatThrownBy(() -> service.subscribe(999L, host))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_FOUND);
    }

    private Party openParty(Long creatorId) {
        return Party.open(creatorId,
                new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3),
                Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
    }
}
