package team.codingforest.moyeota.matching.party.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.Radius;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.profiles.active=dev")
class PartyLockQueryPostgresTest {
    @Autowired Parties parties;
    @Autowired TransactionTemplate tx;

    @Test
    void 잠금_조회가_멤버까지_한_번에_가져온다() {
        Party party = Party.open(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
        party.join(2L);
        Long id = parties.save(party).getId();

        Party locked = tx.execute(status -> parties.findByIdForUpdate(id).orElseThrow());

        assertThat(locked.getMembers()).extracting(PartyMember::getMemberId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 멤버가_없는_방도_잠금_조회가_된다() {
        Party party = Party.open(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
        Long id = parties.save(party).getId();
        tx.executeWithoutResult(status -> {
            Party p = parties.findByIdForUpdate(id).orElseThrow();
            p.leave(1L);
            parties.save(p);
        });

        Party locked = tx.execute(status -> parties.findByIdForUpdate(id).orElseThrow());

        assertThat(locked.getMembers()).isEmpty();
    }
}
