package team.codingforest.moyeota.matching.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.PartyService;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.matching.party.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.sse.infrastructure.PartySseRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PartySseServiceUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long MEMBER_ID = 1L;
    private static final Long STRANGER_ID = 2L;
    private static final Instant BASE_TIME = Instant.parse("2026-10-01T00:00:00Z");

    @Mock
    private Parties parties;
    @Mock
    private PartyService partyService;
    @Mock
    private PartySseRegistry partySseRegistry;

    @InjectMocks
    private PartySseService partySseService;

    @DisplayName("subscribe - 멤버면 레지스트리가 만든 emitter 를 돌려주고 방 상세는 아직 조회하지 않는다")
    @Test
    void subscribe() {

        /*
         * settings
         */
        SseEmitter emitter = new SseEmitter(0L);

        doReturn(Optional.of(party(MEMBER_ID))).when(parties).findById(eq(PARTY_ID));
        doReturn(emitter).when(partySseRegistry).subscribe(eq(PARTY_ID), eq(MEMBER_ID), any());

        /*
         * test
         */
        SseEmitter result = partySseService.subscribe(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isSameAs(emitter);
        verifyNoInteractions(partyService);
    }

    @DisplayName("subscribe - 레지스트리에 넘긴 스냅샷은 호출될 때 그 방의 상세를 조회한다")
    @Test
    void subscribe_snapshot() {

        /*
         * settings
         */
        PartyDetailResult detail = PartyDetailResult.from(party(MEMBER_ID), Map.of(), Map.of());

        doReturn(Optional.of(party(MEMBER_ID))).when(parties).findById(eq(PARTY_ID));
        doReturn(detail).when(partyService).getPartyDetail(eq(PARTY_ID));

        /*
         * test
         */
        partySseService.subscribe(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        ArgumentCaptor<Supplier<Object>> captor = supplierCaptor();
        verify(partySseRegistry).subscribe(eq(PARTY_ID), eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue().get()).isSameAs(detail);
        verify(partyService).getPartyDetail(eq(PARTY_ID));
    }

    @DisplayName("subscribe - 스냅샷 조회가 실패하면 예외를 스냅샷 호출자(레지스트리)에게 그대로 넘긴다")
    @Test
    void subscribe_snapshotFailed() {

        /*
         * settings
         */
        doReturn(Optional.of(party(MEMBER_ID))).when(parties).findById(eq(PARTY_ID));
        doThrow(new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND)).when(partyService).getPartyDetail(eq(PARTY_ID));

        /*
         * test
         */
        partySseService.subscribe(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        ArgumentCaptor<Supplier<Object>> captor = supplierCaptor();
        verify(partySseRegistry).subscribe(eq(PARTY_ID), eq(MEMBER_ID), captor.capture());
        assertBusinessException(catchThrowable(() -> captor.getValue().get()), MatchingErrorCode.PARTY_NOT_FOUND);
    }

    @DisplayName("subscribe - 없는 방이면 PARTY_NOT_FOUND 이고 구독하지 않는다")
    @Test
    void subscribe_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partySseService.subscribe(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(partySseRegistry, partyService);
    }

    @DisplayName("subscribe - 멤버가 아니면 NOT_PARTY_MEMBER 이고 구독하지 않는다")
    @Test
    void subscribe_notMember() {

        /*
         * settings
         */
        doReturn(Optional.of(party(MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partySseService.subscribe(PARTY_ID, STRANGER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_PARTY_MEMBER);
        verifyNoInteractions(partySseRegistry, partyService);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Supplier<Object>> supplierCaptor() {
        return ArgumentCaptor.forClass(Supplier.class);
    }

    private static Party party(Long memberId) {
        return Party.load(PARTY_ID, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역",
                new Capacity(3), List.of(PartyMember.load(memberId, BASE_TIME)), BASE_TIME, PartyStatus.ACTIVE, 12000, 25, "encoded-path", null, null, null, 0L);
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
