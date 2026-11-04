package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Parties;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PartyRecruitingSweeperUnitTest {
    @Mock private Parties parties;
    @Mock private PartyService partyService;
    @Mock private ReservationReminderService reminderService;
    @InjectMocks private PartyRecruitingSweeper sweeper;

    @Test
    void 실시간_방은_15분이_지나면_만료_대상으로_찾는다() {
        doReturn(List.of()).when(parties).findRecruitingExpired(any(), any());

        sweeper.sweep();

        ArgumentCaptor<Instant> createdBefore = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(parties).findRecruitingExpired(createdBefore.capture(), now.capture());
        assertThat(Duration.between(createdBefore.getValue(), now.getValue())).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void 한_방이_실패해도_나머지_방을_계속_처리한다() {
        doReturn(List.of(1L, 2L)).when(parties).findRecruitingExpired(any(), any());
        doThrow(new BusinessException(MatchingErrorCode.PARTY_CLOSED)).when(partyService).expireRecruiting(1L);

        sweeper.sweep();

        verify(partyService).expireRecruiting(2L);
    }

    @Test
    void 알림은_30분_안에_출발하는_방을_찾는다() {
        doReturn(List.of()).when(parties).findReservationsToRemind(any(), any());

        sweeper.remind();

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(parties).findReservationsToRemind(from.capture(), to.capture());
        assertThat(Duration.between(from.getValue(), to.getValue())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void 한_방의_알림이_실패해도_나머지_방에_보낸다() {
        doReturn(List.of(1L, 2L)).when(parties).findReservationsToRemind(any(), any());
        doThrow(new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND)).when(reminderService).remind(1L);

        sweeper.remind();

        verify(reminderService).remind(2L);
    }
}
