package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
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

/**
 *  예약 방 출발 30분 전 알림 - 언제 보낼지는 스케줄러가 departure_at 구간으로 고르고, 여기서는 받는 사람에게 보내기만 한다
 */
class ReservationReminderServiceTest {
    private final List<Long> notified = new ArrayList<>();
    private final List<List<Long>> notifiedMembers = new ArrayList<>();
    private final List<Instant> notifiedDepartures = new ArrayList<>();
    private final ReservationReminderNotifier notifier = (partyId, memberIds, departureAt) -> {
        notified.add(partyId);
        notifiedMembers.add(memberIds);
        notifiedDepartures.add(departureAt);
    };

    private PartyJpaTest parties;
    private ReservationReminderService service;

    @BeforeEach
    void setUp() {
        parties = new PartyJpaTest();
        service = new ReservationReminderService(parties, notifier);
    }

    private Party savedReservation() {
        return parties.save(Party.reserve(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), "강남역", "판교역",
                new Capacity(3), Instant.now(), Instant.now().plus(Duration.ofMinutes(20))));
    }

    @Test
    void 구성원_모두에게_출발_시각과_함께_알린다() {
        Party party = savedReservation();
        party.join(2L, Instant.now());
        parties.save(party);

        service.remind(party.getId());

        assertThat(notified).containsExactly(party.getId());
        assertThat(notifiedMembers.get(0)).containsExactly(1L, 2L);
        assertThat(notifiedDepartures.get(0)).isEqualTo(party.getDepartureAt());
    }

    @Test
    void 방을_바꾸지_않는다() {
        // 보냈는지는 기록하지 않는다 - 같은 방을 다시 고르지 않는 것은 스케줄러의 겹치지 않는 1분 구간이 맡는다
        Party party = savedReservation();
        party.join(2L, Instant.now());
        Party saved = parties.save(party);
        long version = saved.getVersion();

        service.remind(saved.getId());

        assertThat(parties.findById(saved.getId()).orElseThrow().getVersion()).isEqualTo(version);
    }

    @Test
    void 혼자인_예약_방에는_보내지_않는다() {
        // 출발 시각까지 혼자면 모집 만료로 닫힐 방이라 "출발 전" 알림이 맞지 않는다
        Long id = savedReservation().getId();

        service.remind(id);

        assertThat(notified).isEmpty();
    }

    @Test
    void 취소된_방에는_보내지_않는다() {
        Party party = savedReservation();
        party.join(2L, Instant.now());
        party.leave(2L);
        party.leave(1L);
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
