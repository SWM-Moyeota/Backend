package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompletedPartySweeperUnitTest {
    @Mock
    private Parties parties;
    @Mock
    private PartyService partyService;

    @InjectMocks
    private CompletedPartySweeper sweeper;

    @DisplayName("sweep - 지금에서 1일 전을 기준 시각으로 정리 대상을 찾는다")
    @Test
    void sweep_cutoff() {

        /*
         * settings
         */
        doReturn(List.of()).when(parties).findAutoCloseTargets(any());
        Instant before = Instant.now();

        /*
         * test
         */
        sweeper.sweep();

        /*
         * verify
         */
        Instant after = Instant.now();
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(parties).findAutoCloseTargets(cutoff.capture());
        assertThat(CompletedPartySweeper.AUTO_CLOSE_AFTER).isEqualTo(Duration.ofDays(1));
        assertThat(cutoff.getValue()).isBetween(before.minus(Duration.ofDays(1)), after.minus(Duration.ofDays(1)));
    }

    @DisplayName("sweep - 찾은 방마다 자동 정리를 맡긴다")
    @Test
    void sweep() {

        /*
         * settings
         */
        doReturn(List.of(1L, 2L)).when(parties).findAutoCloseTargets(any());

        /*
         * test
         */
        sweeper.sweep();

        /*
         * verify
         */
        verify(partyService).autoClose(eq(1L));
        verify(partyService).autoClose(eq(2L));
    }

    @DisplayName("sweep - 한 방이 BusinessException 으로 거절돼도 나머지 방을 계속 정리한다")
    @Test
    void sweep_continuesAfterBusinessException() {

        /*
         * settings
         */
        doReturn(List.of(1L, 2L, 3L)).when(parties).findAutoCloseTargets(any());
        doThrow(new BusinessException(MatchingErrorCode.PARTY_CLOSED)).when(partyService).autoClose(eq(1L));
        doThrow(new BusinessException(MatchingErrorCode.MEETING_NOT_STARTED)).when(partyService).autoClose(eq(2L));

        /*
         * test
         */
        sweeper.sweep();

        /*
         * verify
         */
        verify(partyService).autoClose(eq(3L));
    }

    @DisplayName("sweep - 대상이 없으면 아무 방도 정리하지 않는다")
    @Test
    void sweep_noTargets() {

        /*
         * settings
         */
        doReturn(List.of()).when(parties).findAutoCloseTargets(any());

        /*
         * test
         */
        sweeper.sweep();

        /*
         * verify
         */
        verify(partyService, never()).autoClose(anyLong());
    }
}
