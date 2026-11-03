package team.codingforest.moyeota.user.sanction.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanction;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanctions;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NoShowPenaltyServiceUnitTest {
    private static final Long USER_ID = 1L;
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T08:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-03T09:00:00Z");

    @Mock
    private NoShowSanctions sanctions;
    @InjectMocks
    private NoShowPenaltyService service;

    // ───────────────────────── record ─────────────────────────

    @DisplayName("record - 행을 잠가 읽은 제재에 노쇼 1번을 더해 저장한다(누적 1회 → 2회, 약속 시간부터 48시간)")
    @Test
    void record() {

        /*
         * settings
         */
        doReturn(Optional.of(new NoShowSanction(1, NOW.minus(Duration.ofDays(3)), null))).when(sanctions).findForUpdate(eq(USER_ID));

        /*
         * test
         */
        service.record(USER_ID, MEETING_AT, NOW);

        /*
         * verify
         */
        InOrder order = inOrder(sanctions);
        order.verify(sanctions).findForUpdate(eq(USER_ID));
        order.verify(sanctions).save(eq(USER_ID), eq(new NoShowSanction(2, NOW, MEETING_AT.plus(Duration.ofHours(48)))));
    }

    @DisplayName("record - 사용자가 없으면 저장하지 않고 넘어간다(출발·자동 정리를 되돌리지 않는다)")
    @Test
    void record_userNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(sanctions).findForUpdate(eq(USER_ID));

        /*
         * test
         */
        service.record(USER_ID, MEETING_AT, NOW);

        /*
         * verify
         */
        verify(sanctions, never()).save(any(), any());
    }

    // ───────────────────────── cancel ─────────────────────────

    @DisplayName("cancel - 누적 횟수를 1 줄이고 제한을 풀어 저장한다")
    @Test
    void cancel() {

        /*
         * settings
         */
        doReturn(Optional.of(new NoShowSanction(2, NOW.minus(Duration.ofDays(1)), MEETING_AT.plus(Duration.ofHours(48)))))
                .when(sanctions).findForUpdate(eq(USER_ID));

        /*
         * test
         */
        service.cancel(USER_ID, NOW);

        /*
         * verify
         */
        verify(sanctions).save(eq(USER_ID), eq(new NoShowSanction(1, NOW, null)));
    }

    @DisplayName("cancel - 누적 0회에서 취소해도 0 이다")
    @Test
    void cancel_floorAtZero() {

        /*
         * settings
         */
        doReturn(Optional.of(NoShowSanction.NONE)).when(sanctions).findForUpdate(eq(USER_ID));

        /*
         * test
         */
        service.cancel(USER_ID, NOW);

        /*
         * verify
         */
        verify(sanctions).save(eq(USER_ID), eq(new NoShowSanction(0, NOW, null)));
    }

    @DisplayName("cancel - 사용자가 없으면 저장하지 않는다")
    @Test
    void cancel_userNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(sanctions).findForUpdate(eq(USER_ID));

        /*
         * test
         */
        service.cancel(USER_ID, NOW);

        /*
         * verify
         */
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
        doReturn(Optional.of(new NoShowSanction(1, NOW, until))).when(sanctions).find(eq(USER_ID));

        /*
         * test
         */
        Optional<Instant> result = service.findRestrictedUntil(USER_ID, NOW);

        /*
         * verify
         */
        assertThat(result).contains(until);
    }

    @DisplayName("findRestrictedUntil - 제한이 끝났으면 empty")
    @Test
    void findRestrictedUntil_expired() {

        /*
         * settings
         */
        doReturn(Optional.of(new NoShowSanction(1, NOW, NOW.minusSeconds(1)))).when(sanctions).find(eq(USER_ID));

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
