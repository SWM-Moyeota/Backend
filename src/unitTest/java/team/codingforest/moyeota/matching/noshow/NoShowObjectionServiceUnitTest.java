package team.codingforest.moyeota.matching.noshow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.noshow.domain.NoShowHistory;
import team.codingforest.moyeota.matching.noshow.domain.NoShowObjection;
import team.codingforest.moyeota.matching.noshow.domain.NoShowObjections;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionStatus;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionSummary;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;
import team.codingforest.moyeota.user.api.NoShowPenalty;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NoShowObjectionServiceUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long OBJECTION_ID = 7L;
    private static final Long HOST_ID = 1L;
    private static final Long NO_SHOW_ID = 2L;
    private static final Long VOIDED_ID = 3L;
    private static final Long OUTSIDER_ID = 99L;
    private static final String REASON = "도착 버튼이 눌리지 않았습니다";
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant NO_SHOW_AT = MEETING_AT.plus(Duration.ofMinutes(10));
    private static final Instant VOIDED_AT = MEETING_AT.plus(Duration.ofDays(1));
    private static final Instant CREATED_AT = MEETING_AT.plus(Duration.ofHours(2));

    @Mock
    private Parties parties;
    @Mock
    private NoShowObjections objections;
    @Mock
    private NoShowPenalty noShowPenalty;
    @InjectMocks
    private NoShowObjectionService service;

    // ───────────────────────── object ─────────────────────────

    @DisplayName("object - 이 방의 노쇼 멤버면 PENDING 이의를 저장한다")
    @Test
    void object() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedParty())).when(parties).findById(eq(PARTY_ID));
        doReturn(false).when(objections).exists(eq(NO_SHOW_ID), eq(PARTY_ID));
        doReturn(NoShowObjection.load(OBJECTION_ID, NO_SHOW_ID, PARTY_ID, REASON, ObjectionStatus.PENDING, CREATED_AT, null))
                .when(objections).save(any(NoShowObjection.class));

        /*
         * test
         */
        service.object(PARTY_ID, NO_SHOW_ID, REASON);

        /*
         * verify
         */
        ArgumentCaptor<NoShowObjection> captor = ArgumentCaptor.forClass(NoShowObjection.class);
        verify(objections).save(captor.capture());
        NoShowObjection saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getUserId()).isEqualTo(NO_SHOW_ID);
        assertThat(saved.getPartyId()).isEqualTo(PARTY_ID);
        assertThat(saved.getReason()).isEqualTo(REASON);
        assertThat(saved.getStatus()).isEqualTo(ObjectionStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
        verifyNoInteractions(noShowPenalty);
        verify(parties, never()).save(any());
    }

    @DisplayName("object - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void object_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.object(PARTY_ID, NO_SHOW_ID, REASON));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(objections);
    }

    @DisplayName("object - 방의 멤버가 아니면 NOT_PARTY_MEMBER 이고 저장하지 않는다")
    @Test
    void object_notMember() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedParty())).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.object(PARTY_ID, OUTSIDER_ID, REASON));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_PARTY_MEMBER);
        verifyNoInteractions(objections);
    }

    @DisplayName("object - 도착해 탄 멤버(노쇼가 아님)면 NOT_NO_SHOW")
    @Test
    void object_notNoShow() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedParty())).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.object(PARTY_ID, HOST_ID, REASON));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_NO_SHOW);
        verifyNoInteractions(objections);
    }

    @DisplayName("object - 이미 무효로 돌아간 노쇼면 NOT_NO_SHOW")
    @Test
    void object_voided() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedParty())).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.object(PARTY_ID, VOIDED_ID, REASON));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_NO_SHOW);
        verifyNoInteractions(objections);
    }

    @DisplayName("object - 이미 이의를 냈으면 ALREADY_OBJECTED 이고 저장하지 않는다")
    @Test
    void object_alreadyObjected() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedParty())).when(parties).findById(eq(PARTY_ID));
        doReturn(true).when(objections).exists(eq(NO_SHOW_ID), eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.object(PARTY_ID, NO_SHOW_ID, REASON));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_OBJECTED);
        verify(objections, never()).save(any());
    }

    @DisplayName("object - 확인과 저장 사이에 같은 신청이 먼저 들어가 유니크 제약에 걸리면 ALREADY_OBJECTED")
    @Test
    void object_raceOnUniqueConstraint() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedParty())).when(parties).findById(eq(PARTY_ID));
        doReturn(false).when(objections).exists(eq(NO_SHOW_ID), eq(PARTY_ID));
        doThrow(new DataIntegrityViolationException("uk_no_show_objection_user_match")).when(objections).save(any(NoShowObjection.class));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.object(PARTY_ID, NO_SHOW_ID, REASON));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_OBJECTED);
    }

    @DisplayName("object - 사유가 공백뿐이면 IllegalArgumentException 이고 저장하지 않는다")
    @Test
    void object_blankReason() {

        /*
         * settings
         */
        doReturn(Optional.of(finishedParty())).when(parties).findById(eq(PARTY_ID));
        doReturn(false).when(objections).exists(eq(NO_SHOW_ID), eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.object(PARTY_ID, NO_SHOW_ID, "   "));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        verify(objections, never()).save(any());
    }

    // ───────────────────────── findMyNoShows / findObjections ─────────────────────────

    @DisplayName("findMyNoShows - 저장소의 노쇼 기록을 최대 20개로 그대로 준다")
    @Test
    void findMyNoShows() {

        /*
         * settings
         */
        List<NoShowHistory> histories = List.of(new NoShowHistory(PARTY_ID, "강남역", "판교역", MEETING_AT, NO_SHOW_AT, null, ObjectionStatus.PENDING));
        doReturn(histories).when(objections).findNoShowHistory(eq(NO_SHOW_ID), eq(20));

        /*
         * test
         */
        List<NoShowHistory> result = service.findMyNoShows(NO_SHOW_ID);

        /*
         * verify
         */
        assertThat(result).isSameAs(histories);
    }

    @DisplayName("findObjections - 상태별 목록을 최대 100개로 그대로 준다")
    @Test
    void findObjections() {

        /*
         * settings
         */
        List<ObjectionSummary> summaries = List.of(new ObjectionSummary(OBJECTION_ID, NO_SHOW_ID, PARTY_ID, "강남역", "판교역", MEETING_AT,
                REASON, ObjectionStatus.PENDING, CREATED_AT, null));
        doReturn(summaries).when(objections).findByStatus(eq(ObjectionStatus.PENDING), eq(100));

        /*
         * test
         */
        List<ObjectionSummary> result = service.findObjections(ObjectionStatus.PENDING);

        /*
         * verify
         */
        assertThat(result).isSameAs(summaries);
    }

    // ───────────────────────── accept ─────────────────────────

    @DisplayName("accept - 신청을 잠가 ACCEPTED 로 바꾸고, 방을 잠가 멤버의 노쇼를 무효로 저장한 뒤 그 방의 제재를 취소한다")
    @Test
    void accept() {

        /*
         * settings
         */
        NoShowObjection objection = pending(NO_SHOW_ID);
        Party party = finishedParty();
        doReturn(Optional.of(objection)).when(objections).findByIdForUpdate(eq(OBJECTION_ID));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.accept(OBJECTION_ID);

        /*
         * verify
         */
        assertThat(objection.getStatus()).isEqualTo(ObjectionStatus.ACCEPTED);
        assertThat(objection.getResolvedAt()).isNotNull();
        PartyMember member = memberOf(party, NO_SHOW_ID);
        assertThat(member.isNoShow()).isFalse();
        assertThat(member.getNoShowVoidedAt()).isEqualTo(objection.getResolvedAt());

        InOrder order = inOrder(objections, parties, noShowPenalty);
        order.verify(objections).findByIdForUpdate(eq(OBJECTION_ID));
        order.verify(parties).findByIdForUpdate(eq(PARTY_ID));
        order.verify(parties).save(eq(party));
        order.verify(noShowPenalty).cancel(eq(NO_SHOW_ID), eq(PARTY_ID), eq(objection.getResolvedAt()));
        order.verify(objections).save(eq(objection));
    }

    @DisplayName("accept - 다른 경로로 이미 무효가 된 노쇼면 제재를 두 번 줄이지 않고 신청만 처리한다")
    @Test
    void accept_alreadyVoided() {

        /*
         * settings
         */
        NoShowObjection objection = pending(VOIDED_ID);
        Party party = finishedParty();
        doReturn(Optional.of(objection)).when(objections).findByIdForUpdate(eq(OBJECTION_ID));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.accept(OBJECTION_ID);

        /*
         * verify
         */
        assertThat(objection.getStatus()).isEqualTo(ObjectionStatus.ACCEPTED);
        assertThat(memberOf(party, VOIDED_ID).getNoShowVoidedAt()).isEqualTo(VOIDED_AT);
        verifyNoInteractions(noShowPenalty);
        verify(objections).save(eq(objection));
    }

    @DisplayName("accept - 이미 처리된 신청이면 OBJECTION_ALREADY_RESOLVED 이고 방·제재를 건드리지 않는다")
    @Test
    void accept_alreadyResolved() {

        /*
         * settings
         */
        NoShowObjection objection = NoShowObjection.load(OBJECTION_ID, NO_SHOW_ID, PARTY_ID, REASON, ObjectionStatus.REJECTED, CREATED_AT, CREATED_AT.plusSeconds(60));
        doReturn(Optional.of(objection)).when(objections).findByIdForUpdate(eq(OBJECTION_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.accept(OBJECTION_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.OBJECTION_ALREADY_RESOLVED);
        verifyNoInteractions(parties, noShowPenalty);
        verify(objections, never()).save(any());
    }

    @DisplayName("accept - 없는 신청이면 OBJECTION_NOT_FOUND")
    @Test
    void accept_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(objections).findByIdForUpdate(eq(OBJECTION_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.accept(OBJECTION_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.OBJECTION_NOT_FOUND);
        verifyNoInteractions(parties, noShowPenalty);
    }

    @DisplayName("accept - 방이 없으면 PARTY_NOT_FOUND 이고 제재를 건드리지 않는다")
    @Test
    void accept_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.of(pending(NO_SHOW_ID))).when(objections).findByIdForUpdate(eq(OBJECTION_ID));
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.accept(OBJECTION_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(noShowPenalty);
        verify(objections, never()).save(any());
    }

    // ───────────────────────── reject ─────────────────────────

    @DisplayName("reject - REJECTED 로 바꿔 저장하고 방·제재는 건드리지 않는다")
    @Test
    void reject() {

        /*
         * settings
         */
        NoShowObjection objection = pending(NO_SHOW_ID);
        doReturn(Optional.of(objection)).when(objections).findByIdForUpdate(eq(OBJECTION_ID));

        /*
         * test
         */
        service.reject(OBJECTION_ID);

        /*
         * verify
         */
        assertThat(objection.getStatus()).isEqualTo(ObjectionStatus.REJECTED);
        assertThat(objection.getResolvedAt()).isNotNull();
        verify(objections).save(eq(objection));
        verifyNoInteractions(parties, noShowPenalty);
    }

    @DisplayName("reject - 이미 처리된 신청이면 OBJECTION_ALREADY_RESOLVED")
    @Test
    void reject_alreadyResolved() {

        /*
         * settings
         */
        NoShowObjection objection = NoShowObjection.load(OBJECTION_ID, NO_SHOW_ID, PARTY_ID, REASON, ObjectionStatus.ACCEPTED, CREATED_AT, CREATED_AT.plusSeconds(60));
        doReturn(Optional.of(objection)).when(objections).findByIdForUpdate(eq(OBJECTION_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.reject(OBJECTION_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.OBJECTION_ALREADY_RESOLVED);
        verify(objections, never()).save(any());
        verifyNoInteractions(parties, noShowPenalty);
    }

    @DisplayName("reject - 없는 신청이면 OBJECTION_NOT_FOUND")
    @Test
    void reject_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(objections).findByIdForUpdate(anyLong());

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.reject(OBJECTION_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.OBJECTION_NOT_FOUND);
    }

    // ───────────────────────── 도우미 ─────────────────────────

    private static NoShowObjection pending(Long userId) {
        return NoShowObjection.load(OBJECTION_ID, userId, PARTY_ID, REASON, ObjectionStatus.PENDING, CREATED_AT, null);
    }

    /** 방장은 도착해 탔고, NO_SHOW_ID 는 노쇼, VOIDED_ID 는 이미 무효로 돌아간 노쇼인 끝난 확정 방 */
    private static Party finishedParty() {
        List<PartyMember> members = new ArrayList<>(List.of(
                PartyMember.load(HOST_ID, MEETING_AT.minus(Duration.ofMinutes(30)), MEETING_AT, null, null),
                PartyMember.load(NO_SHOW_ID, MEETING_AT.minus(Duration.ofMinutes(20)), null, NO_SHOW_AT, null),
                PartyMember.load(VOIDED_ID, MEETING_AT.minus(Duration.ofMinutes(10)), null, NO_SHOW_AT, VOIDED_AT)));

        return Party.load(PARTY_ID, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(3), members,
                HOST_ID, MEETING_AT.minus(Duration.ofHours(1)), PartyStatus.FINISHED, 12000, 25, "encoded-path",
                7L, RoomType.REALTIME, MEETING_AT, null);
    }

    private static PartyMember memberOf(Party party, Long memberId) {
        return party.getMembers().stream()
                .filter(m -> m.getMemberId().equals(memberId))
                .findFirst()
                .orElseThrow();
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
