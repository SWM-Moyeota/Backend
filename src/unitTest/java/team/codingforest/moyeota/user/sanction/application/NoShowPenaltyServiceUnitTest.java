package team.codingforest.moyeota.user.sanction.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.user.sanction.domain.NoShow;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanction;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanctions;
import team.codingforest.moyeota.user.sanction.domain.NoShows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NoShowPenaltyServiceUnitTest {
    private static final Long USER_ID = 1L;
    private static final Long PARTY_ID = 100L;
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T08:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-03T09:00:00Z");

    @Mock
    private NoShowSanctions sanctions;
    @Mock
    private NoShows noShows;
    @InjectMocks
    private NoShowPenaltyService service;

    // ───────────────────────── record ─────────────────────────

    @DisplayName("record - users 행을 잠근 뒤 노쇼를 더하고, 이번 노쇼까지 센 횟수로 제한을 저장한다(2건 → 약속 시간부터 48시간)")
    @Test
    void record() {

        /*
         * settings
         */
        doReturn(Optional.of(NoShowSanction.NONE)).when(sanctions).findForUpdate(eq(USER_ID));
        doReturn(true).when(noShows).add(eq(USER_ID), eq(PARTY_ID), eq(MEETING_AT));
        doReturn(List.of(new NoShow(MEETING_AT.minus(Duration.ofDays(3)), null), new NoShow(MEETING_AT, null)))
                .when(noShows).findUnexpired(eq(USER_ID), eq(NOW));

        /*
         * test
         */
        service.record(USER_ID, PARTY_ID, MEETING_AT, NOW);

        /*
         * verify
         */
        InOrder order = inOrder(sanctions, noShows);
        order.verify(sanctions).findForUpdate(eq(USER_ID));
        order.verify(noShows).add(eq(USER_ID), eq(PARTY_ID), eq(MEETING_AT));
        order.verify(noShows).findUnexpired(eq(USER_ID), eq(NOW));
        order.verify(sanctions).save(eq(USER_ID), eq(new NoShowSanction(MEETING_AT.plus(Duration.ofHours(48)))));
    }

    @DisplayName("record - 무효로 돌린 노쇼는 세지 않는다(유효 1건 → 24시간)")
    @Test
    void record_ignoresVoided() {

        /*
         * settings
         */
        doReturn(Optional.of(NoShowSanction.NONE)).when(sanctions).findForUpdate(eq(USER_ID));
        doReturn(true).when(noShows).add(eq(USER_ID), eq(PARTY_ID), eq(MEETING_AT));
        doReturn(List.of(new NoShow(MEETING_AT.minus(Duration.ofDays(3)), NOW), new NoShow(MEETING_AT, null)))
                .when(noShows).findUnexpired(eq(USER_ID), eq(NOW));

        /*
         * test
         */
        service.record(USER_ID, PARTY_ID, MEETING_AT, NOW);

        /*
         * verify
         */
        verify(sanctions).save(eq(USER_ID), eq(new NoShowSanction(MEETING_AT.plus(Duration.ofHours(24)))));
    }

    @DisplayName("record - 같은 방의 노쇼가 이미 기록돼 있으면 다시 세지도 제재를 바꾸지도 않는다")
    @Test
    void record_alreadyRecorded() {

        /*
         * settings
         */
        doReturn(Optional.of(new NoShowSanction(MEETING_AT.plus(Duration.ofHours(24))))).when(sanctions).findForUpdate(eq(USER_ID));
        doReturn(false).when(noShows).add(eq(USER_ID), eq(PARTY_ID), eq(MEETING_AT));

        /*
         * test
         */
        service.record(USER_ID, PARTY_ID, MEETING_AT, NOW);

        /*
         * verify
         */
        verify(noShows, never()).findUnexpired(any(), any());
        verify(sanctions, never()).save(any(), any());
    }

    @DisplayName("record - 사용자가 없으면 노쇼도 제재도 저장하지 않고 넘어간다(출발·자동 정리를 되돌리지 않는다)")
    @Test
    void record_userNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(sanctions).findForUpdate(eq(USER_ID));

        /*
         * test
         */
        service.record(USER_ID, PARTY_ID, MEETING_AT, NOW);

        /*
         * verify
         */
        verifyNoInteractions(noShows);
        verify(sanctions, never()).save(any(), any());
    }

    // ───────────────────────── cancel ─────────────────────────

    @DisplayName("cancel - users 행을 잠근 뒤 그 방의 노쇼를 무효로 돌리고 제한을 풀어 저장한다")
    @Test
    void cancel() {

        /*
         * settings
         */
        doReturn(Optional.of(new NoShowSanction(MEETING_AT.plus(Duration.ofHours(48))))).when(sanctions).findForUpdate(eq(USER_ID));
        doReturn(true).when(noShows).markVoided(eq(USER_ID), eq(PARTY_ID), eq(NOW));

        /*
         * test
         */
        service.cancel(USER_ID, PARTY_ID, NOW);

        /*
         * verify
         */
        InOrder order = inOrder(sanctions, noShows);
        order.verify(sanctions).findForUpdate(eq(USER_ID));
        order.verify(noShows).markVoided(eq(USER_ID), eq(PARTY_ID), eq(NOW));
        order.verify(sanctions).save(eq(USER_ID), eq(NoShowSanction.NONE));
    }

    @DisplayName("cancel - 그 방의 노쇼가 없거나 이미 무효면 제한을 그대로 둔다")
    @Test
    void cancel_noShowNotFound() {

        /*
         * settings
         */
        doReturn(Optional.of(new NoShowSanction(MEETING_AT.plus(Duration.ofHours(48))))).when(sanctions).findForUpdate(eq(USER_ID));
        doReturn(false).when(noShows).markVoided(eq(USER_ID), eq(PARTY_ID), eq(NOW));

        /*
         * test
         */
        service.cancel(USER_ID, PARTY_ID, NOW);

        /*
         * verify
         */
        verify(sanctions, never()).save(any(), any());
    }

    @DisplayName("cancel - 사용자가 없으면 아무것도 저장하지 않는다")
    @Test
    void cancel_userNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(sanctions).findForUpdate(eq(USER_ID));

        /*
         * test
         */
        service.cancel(USER_ID, PARTY_ID, NOW);

        /*
         * verify
         */
        verifyNoInteractions(noShows);
        verify(sanctions, never()).save(any(), any());
    }

    // ───────────────────────── findRestrictedUntil ─────────────────────────

    @DisplayName("findRestrictedUntil - 제한 중이면 끝 시각을 준다")
    @Test
    void findRestrictedUntil_restricted() {

        /*
         * settings
         */
        Instant until = NOW.plus(Duration.ofHours(1));
        doReturn(Optional.of(new NoShowSanction(until))).when(sanctions).find(eq(USER_ID));

        /*
         * test
         */
        Optional<Instant> result = service.findRestrictedUntil(USER_ID, NOW);

        /*
         * verify
         */
        assertThat(result).contains(until);
        verifyNoInteractions(noShows);
    }

    @DisplayName("findRestrictedUntil - 제한이 끝났으면 empty")
    @Test
    void findRestrictedUntil_expired() {

        /*
         * settings
         */
        doReturn(Optional.of(new NoShowSanction(NOW.minusSeconds(1)))).when(sanctions).find(eq(USER_ID));

        /*
         * test
         */
        Optional<Instant> result = service.findRestrictedUntil(USER_ID, NOW);

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    @DisplayName("findRestrictedUntil - 제한이 없거나 사용자가 없으면 empty")
    @Test
    void findRestrictedUntil_none() {

        /*
         * settings
         */
        doReturn(Optional.of(NoShowSanction.NONE)).when(sanctions).find(eq(USER_ID));
        doReturn(Optional.empty()).when(sanctions).find(eq(2L));

        /*
         * test & verify
         */
        assertThat(service.findRestrictedUntil(USER_ID, NOW)).isEmpty();
        assertThat(service.findRestrictedUntil(2L, NOW)).isEmpty();
    }
}
