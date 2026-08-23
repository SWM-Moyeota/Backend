package team.codingforest.moyeota.matching.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.matching.domain.Capacity;
import team.codingforest.moyeota.matching.domain.Location;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.Radius;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({PartyJpa.class, JpaAuditingConfig.class})
class PartyJpaPersistenceTest {
    private final PartyJpa parties;
    private final PartyJpaRepository repository;

    @Autowired
    PartyJpaPersistenceTest(PartyJpa parties, PartyJpaRepository repository) {
        this.parties = parties;
        this.repository = repository;
    }

    private Party openAndSave() {
        Party party = Party.open(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
        return parties.save(party);
    }

    @Test
    void 기존_방을_다시_저장해도_행이_늘어나지_않는다() {
        Party saved = openAndSave();
        long before = repository.count();

        saved.join(2L);
        parties.save(saved);

        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void 기존_방을_다시_저장하면_같은_id를_유지한다() {
        Party saved = openAndSave();

        saved.join(2L);
        Party after = parties.save(saved);

        assertThat(after.getId()).isEqualTo(saved.getId());
    }

    @Test
    void join_후_저장하면_원래_id로_조회했을_때_멤버가_반영된다() {
        Party saved = openAndSave();

        saved.join(2L);
        parties.save(saved);

        Party reloaded = parties.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMembers()).hasSize(2);
        assertThat(reloaded.hasMember(2L)).isTrue();
    }

    @Test
    void 상태_변경_후_저장하면_원래_id로_조회했을_때_상태가_반영된다() {
        Party saved = openAndSave();

        saved.cancel(1L);
        parties.save(saved);

        assertThat(parties.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @Test
    void 취소된_방의_멤버는_진행_중인_방이_없는_것으로_본다() {
        Party saved = openAndSave();
        saved.join(2L);
        parties.save(saved);

        saved.cancel(1L);
        parties.save(saved);

        assertThat(parties.existsOngoingByMemberId(2L)).isFalse();
    }
}
