package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.ReservationReminderNotifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationReminderServiceTest {
    private final List<Long> notified = new ArrayList<>();
    private final List<List<Long>> notifiedMembers = new ArrayList<>();
    private final ReservationReminderNotifier notifier = (partyId, memberIds, departureAt) -> {
        notified.add(partyId);
        notifiedMembers.add(memberIds);
    };
    private final AfterCommitExecutor immediate = new AfterCommitExecutor() {
        @Override
        public void execute(String name, Runnable task) {
            task.run();
        }
    };

    private PartyJpaTest parties;
    private ReservationReminderService service;

    @BeforeEach
    void setUp() {
        parties = new PartyJpaTest();
        service = new ReservationReminderService(parties, immediate, notifier);
    }

    private Party savedReservation() {
        return parties.save(Party.reserve(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), "강남역", "판교역",
                new Capacity(3), Instant.now(), Instant.now().plus(Duration.ofMinutes(20))));
    }

    @Test
    void 구성원_모두에게_알리고_보낸_시각을_남긴다() {
        Party party = savedReservation();
        party.join(2L);
        parties.save(party);

        service.remind(party.getId());

        assertThat(notified).containsExactly(party.getId());
        assertThat(notifiedMembers.get(0)).containsExactlyInAnyOrder(1L, 2L);
        assertThat(parties.findById(party.getId()).orElseThrow().getReminderSentAt()).isNotNull();
    }

    @Test
    void 이미_보낸_방에는_다시_보내지_않는다() {
        Long id = savedReservation().getId();

        service.remind(id);
        service.remind(id);

        assertThat(notified).hasSize(1);
    }

    @Test
    void 취소된_방에는_보내지_않는다() {
        Party party = savedReservation();
        party.expireRecruiting();
        parties.save(party);

        service.remind(party.getId());

        assertThat(notified).isEmpty();
    }

    @Test
    void 없는_방이면_예외를_던진다() {
        assertThatThrownBy(() -> service.remind(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_FOUND);
    }
}
