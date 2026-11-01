package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.matching.api.dto.MatchingTarget;
import team.codingforest.moyeota.matching.api.dto.PartyChatSummary;
import team.codingforest.moyeota.matching.api.dto.PartySummary;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyChangeNotifier;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.matching.party.dto.PartyClosed;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.record.domain.RideRecords;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PartyAccessServiceUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long STRANGER_ID = 3L;
    private static final Long DRIVER_ID = 9L;
    private static final Long OTHER_DRIVER_ID = 10L;
    private static final Instant BASE_TIME = Instant.parse("2026-10-01T00:00:00Z");
    private static final String SSE_TASK = "party.sse";

    @Mock
    private Parties parties;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private PartyChangeNotifier partyChangeNotifier;
    @Mock
    private RideRecords rideRecords;

    @InjectMocks
    private PartyAccessService partyAccessService;

    // ───────────────────────── findSummary ─────────────────────────

    @DisplayName("findSummary - 방의 좌표·장소·인원·요금·기사를 요약으로 돌려준다")
    @Test
    void findSummary() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.DRIVER_ASSIGNED, DRIVER_ID, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Optional<PartySummary> result = partyAccessService.findSummary(PARTY_ID);

        /*
         * verify
         */
        assertThat(result).contains(new PartySummary(PARTY_ID, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 2, 12000, 25, DRIVER_ID));
    }

    @DisplayName("findSummary - 기사가 배정 전이면 driverId 가 null 이다")
    @Test
    void findSummary_noDriver() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.ACTIVE, null, HOST_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Optional<PartySummary> result = partyAccessService.findSummary(PARTY_ID);

        /*
         * verify
         */
        assertThat(result).isPresent();
        assertThat(result.get().driverId()).isNull();
        assertThat(result.get().memberCount()).isEqualTo(1);
    }

    @DisplayName("findSummary - 없는 방이면 empty")
    @Test
    void findSummary_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Optional<PartySummary> result = partyAccessService.findSummary(PARTY_ID);

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    // ───────────────────────── assignDriver ─────────────────────────

    @DisplayName("assignDriver - MATCHING 방에 기사를 배정해 저장하고 closed 는 보내지 않는다")
    @Test
    void assignDriver() {

        /*
         * settings
         */
        Party party = party(PartyStatus.MATCHING, null, HOST_ID, MEMBER_ID);

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyAccessService.assignDriver(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.DRIVER_ASSIGNED);
        assertThat(party.getTaxiDriverId()).isEqualTo(DRIVER_ID);
        verify(parties).save(eq(party));
        verifyNoInteractions(afterCommitExecutor, partyChangeNotifier);
    }

    @DisplayName("assignDriver - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void assignDriver_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.assignDriver(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verify(parties, never()).save(any());
    }

    @DisplayName("assignDriver - MATCHING 이 아니면 PARTY_NOT_MATCHING 이고 저장하지 않는다")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = "MATCHING", mode = EnumSource.Mode.EXCLUDE)
    void assignDriver_notMatching(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, null, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.assignDriver(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_MATCHING);
        verify(parties, never()).save(any());
    }

    @DisplayName("assignDriver - 이미 기사가 있으면 DRIVER_ALREADY_ASSIGNED 이고 기존 기사를 유지한다")
    @Test
    void assignDriver_alreadyAssigned() {

        /*
         * settings
         */
        Party party = party(PartyStatus.MATCHING, OTHER_DRIVER_ID, HOST_ID, MEMBER_ID);

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.assignDriver(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);
        assertThat(party.getTaxiDriverId()).isEqualTo(OTHER_DRIVER_ID);
        verify(parties, never()).save(any());
    }

    // ───────────────────────── failMatching ─────────────────────────

    @DisplayName("failMatching - MATCHING 방을 CANCELED 로 저장하고 커밋 후 closed 를 보낸다")
    @Test
    void failMatching() {

        /*
         * settings
         */
        Party party = party(PartyStatus.MATCHING, null, HOST_ID, MEMBER_ID);

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyAccessService.failMatching(PARTY_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        verify(parties).save(eq(party));

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("CANCELED")));
    }

    @DisplayName("failMatching - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void failMatching_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.failMatching(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor);
    }

    @DisplayName("failMatching - MATCHING 이 아니면 PARTY_NOT_MATCHING 이고 closed 를 보내지 않는다")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = "MATCHING", mode = EnumSource.Mode.EXCLUDE)
    void failMatching_notMatching(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, null, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.failMatching(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_MATCHING);
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor);
    }

    @DisplayName("failMatching - 기사가 이미 있으면 DRIVER_ALREADY_ASSIGNED 이고 closed 를 보내지 않는다")
    @Test
    void failMatching_driverAssigned() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.MATCHING, DRIVER_ID, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.failMatching(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.DRIVER_ALREADY_ASSIGNED);
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor);
    }

    // ───────────────────────── findMatchingTargets ─────────────────────────

    @DisplayName("findMatchingTargets - 저장소 결과를 그대로 돌려준다")
    @Test
    void findMatchingTargets() {

        /*
         * settings
         */
        List<MatchingTarget> targets = List.of(new MatchingTarget(PARTY_ID, BASE_TIME));

        doReturn(targets).when(parties).findMatchingTargets();

        /*
         * test
         */
        List<MatchingTarget> result = partyAccessService.findMatchingTargets();

        /*
         * verify
         */
        assertThat(result).isEqualTo(targets);
    }

    @DisplayName("findMatchingTargets - 대상이 없으면 빈 목록")
    @Test
    void findMatchingTargets_empty() {

        /*
         * settings
         */
        doReturn(List.of()).when(parties).findMatchingTargets();

        /*
         * test
         */
        List<MatchingTarget> result = partyAccessService.findMatchingTargets();

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    // ───────────────────────── startRide ─────────────────────────

    @DisplayName("startRide - 배정 기사가 탑승 처리하면 IN_RIDE 로 저장하고 탑승 기록을 만든다")
    @Test
    void startRide() {

        /*
         * settings
         */
        Party party = party(PartyStatus.DRIVER_ASSIGNED, DRIVER_ID, HOST_ID, MEMBER_ID);

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        partyAccessService.startRide(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.IN_RIDE);
        verify(parties).save(eq(party));

        ArgumentCaptor<RideRecord> captor = ArgumentCaptor.forClass(RideRecord.class);
        verify(rideRecords).save(captor.capture());
        RideRecord record = captor.getValue();
        assertThat(record.getMatchId()).isEqualTo(PARTY_ID);
        assertThat(record.getTaxiDriverId()).isEqualTo(DRIVER_ID);
        assertThat(record.getDeparturePlace()).isEqualTo("강남역");
        assertThat(record.getDestinationPlace()).isEqualTo("판교역");
        assertThat(record.getPickupTime()).isNotNull();
        assertThat(record.isDroppedOff()).isFalse();
        assertThat(record.getTotalFare()).isNull();
        verifyNoInteractions(afterCommitExecutor);
    }

    @DisplayName("startRide - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void startRide_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.startRide(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(rideRecords);
    }

    @DisplayName("startRide - 배정 기사가 아니면 NOT_ASSIGNED_DRIVER 이고 기록이 없다")
    @Test
    void startRide_notAssignedDriver() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.DRIVER_ASSIGNED, OTHER_DRIVER_ID, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.startRide(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_ASSIGNED_DRIVER);
        verify(parties, never()).save(any());
        verifyNoInteractions(rideRecords);
    }

    @DisplayName("startRide - 기사가 아직 없으면 NOT_ASSIGNED_DRIVER")
    @Test
    void startRide_noDriver() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.MATCHING, null, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.startRide(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_ASSIGNED_DRIVER);
        verifyNoInteractions(rideRecords);
    }

    @DisplayName("startRide - 픽업 대기 상태가 아니면 NOT_AWAITING_PICKUP 이고 기록이 없다")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = "DRIVER_ASSIGNED", mode = EnumSource.Mode.EXCLUDE)
    void startRide_notAwaitingPickup(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, DRIVER_ID, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.startRide(PARTY_ID, DRIVER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_AWAITING_PICKUP);
        verify(parties, never()).save(any());
        verifyNoInteractions(rideRecords);
    }

    // ───────────────────────── completeRide ─────────────────────────

    @DisplayName("completeRide - 탑승 기록에 하차를 찍어 저장하고 커밋 후 closed 를 보낸다")
    @Test
    void completeRide() {

        /*
         * settings
         */
        Party party = party(PartyStatus.IN_RIDE, DRIVER_ID, HOST_ID, MEMBER_ID);
        Instant pickupTime = BASE_TIME.plusSeconds(60);
        RideRecord record = RideRecord.load(PARTY_ID, DRIVER_ID, "강남역", "판교역", pickupTime, null, null);

        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(Optional.of(record)).when(rideRecords).findById(eq(PARTY_ID));

        /*
         * test
         */
        partyAccessService.completeRide(PARTY_ID, DRIVER_ID, 15000);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        verify(parties).save(eq(party));
        verify(rideRecords).save(eq(record));
        assertThat(record.getPickupTime()).isEqualTo(pickupTime);
        assertThat(record.isDroppedOff()).isTrue();
        assertThat(record.getTotalFare()).isEqualTo(15000);

        Runnable task = capturedAfterCommitTask();
        verifyNoInteractions(partyChangeNotifier);
        task.run();
        verify(partyChangeNotifier).closed(eq(PARTY_ID), eq(new PartyClosed("FINISHED")));
    }

    @DisplayName("completeRide - 탑승 기록이 없으면 탑승 시각 없는 기록을 새로 만들어 하차를 찍는다")
    @Test
    void completeRide_noPickupRecord() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.IN_RIDE, DRIVER_ID, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(Optional.empty()).when(rideRecords).findById(eq(PARTY_ID));

        /*
         * test
         */
        partyAccessService.completeRide(PARTY_ID, DRIVER_ID, 15000);

        /*
         * verify
         */
        ArgumentCaptor<RideRecord> captor = ArgumentCaptor.forClass(RideRecord.class);
        verify(rideRecords).save(captor.capture());
        RideRecord record = captor.getValue();
        assertThat(record.getMatchId()).isEqualTo(PARTY_ID);
        assertThat(record.getTaxiDriverId()).isEqualTo(DRIVER_ID);
        assertThat(record.getDeparturePlace()).isEqualTo("강남역");
        assertThat(record.getDestinationPlace()).isEqualTo("판교역");
        assertThat(record.getPickupTime()).isNull();
        assertThat(record.isDroppedOff()).isTrue();
        assertThat(record.getTotalFare()).isEqualTo(15000);
        verify(afterCommitExecutor).execute(eq(SSE_TASK), any());
    }

    @DisplayName("completeRide - 이미 하차한 기록이면 NOT_RIDING 이고 기록을 덮어쓰지 않고 closed 도 보내지 않는다")
    @Test
    void completeRide_alreadyDroppedOff() {

        /*
         * settings
         */
        Instant dropoffTime = BASE_TIME.plusSeconds(600);
        RideRecord record = RideRecord.load(PARTY_ID, DRIVER_ID, "강남역", "판교역", BASE_TIME, dropoffTime, 9000);

        doReturn(Optional.of(party(PartyStatus.IN_RIDE, DRIVER_ID, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));
        doReturn(Optional.of(record)).when(rideRecords).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.completeRide(PARTY_ID, DRIVER_ID, 15000));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_RIDING);
        assertThat(record.getDropoffTime()).isEqualTo(dropoffTime);
        assertThat(record.getTotalFare()).isEqualTo(9000);
        verify(rideRecords, never()).save(any());
        verifyNoInteractions(afterCommitExecutor);
    }

    @DisplayName("completeRide - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void completeRide_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.completeRide(PARTY_ID, DRIVER_ID, 15000));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(rideRecords, afterCommitExecutor);
    }

    @DisplayName("completeRide - 배정 기사가 아니면 NOT_ASSIGNED_DRIVER 이고 기록·신호가 없다")
    @Test
    void completeRide_notAssignedDriver() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.IN_RIDE, OTHER_DRIVER_ID, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.completeRide(PARTY_ID, DRIVER_ID, 15000));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_ASSIGNED_DRIVER);
        verify(parties, never()).save(any());
        verifyNoInteractions(rideRecords, afterCommitExecutor);
    }

    @DisplayName("completeRide - 운행 중이 아니면 NOT_RIDING 이고 기록·신호가 없다")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = "IN_RIDE", mode = EnumSource.Mode.EXCLUDE)
    void completeRide_notRiding(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, DRIVER_ID, HOST_ID, MEMBER_ID))).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.completeRide(PARTY_ID, DRIVER_ID, 15000));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_RIDING);
        verify(parties, never()).save(any());
        verifyNoInteractions(rideRecords, afterCommitExecutor);
    }

    // ───────────────────────── isAwaitingPickup ─────────────────────────

    @DisplayName("isAwaitingPickup - 배정 기사이고 DRIVER_ASSIGNED 면 true")
    @Test
    void isAwaitingPickup() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.DRIVER_ASSIGNED, DRIVER_ID, HOST_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isAwaitingPickup(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isTrue();
    }

    @DisplayName("isAwaitingPickup - 다른 기사면 false")
    @Test
    void isAwaitingPickup_otherDriver() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.DRIVER_ASSIGNED, OTHER_DRIVER_ID, HOST_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isAwaitingPickup(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("isAwaitingPickup - DRIVER_ASSIGNED 가 아니면 false")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = "DRIVER_ASSIGNED", mode = EnumSource.Mode.EXCLUDE)
    void isAwaitingPickup_otherStatus(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, DRIVER_ID, HOST_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isAwaitingPickup(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("isAwaitingPickup - 없는 방이면 예외 없이 false")
    @Test
    void isAwaitingPickup_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isAwaitingPickup(PARTY_ID, DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    // ───────────────────────── findChatSummary ─────────────────────────

    @DisplayName("findChatSummary - 합류 순 멤버와 장소를 돌려준다")
    @Test
    void findChatSummary() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.COMPLETED, null, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        PartyChatSummary result = partyAccessService.findChatSummary(PARTY_ID);

        /*
         * verify
         */
        assertThat(result).isEqualTo(new PartyChatSummary(PARTY_ID, List.of(HOST_ID, MEMBER_ID), "강남역", "판교역"));
    }

    @DisplayName("findChatSummary - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void findChatSummary_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.findChatSummary(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
    }

    // ───────────────────────── hasOngoingRide ─────────────────────────

    @DisplayName("hasOngoingRide - 저장소 결과를 그대로 돌려준다")
    @ParameterizedTest(name = "저장소={0}")
    @ValueSource(booleans = {true, false})
    void hasOngoingRide(boolean ongoing) {

        /*
         * settings
         */
        doReturn(ongoing).when(parties).hasOngoingRide(eq(DRIVER_ID));

        /*
         * test
         */
        boolean result = partyAccessService.hasOngoingRide(DRIVER_ID);

        /*
         * verify
         */
        assertThat(result).isEqualTo(ongoing);
    }

    // ───────────────────────── hasMemberOnParty ─────────────────────────

    @DisplayName("hasMemberOnParty - 방이 있으면 멤버 여부를 저장소 결과 그대로 돌려준다")
    @ParameterizedTest(name = "저장소={0}")
    @ValueSource(booleans = {true, false})
    void hasMemberOnParty(boolean member) {

        /*
         * settings
         */
        doReturn(true).when(parties).existsById(eq(PARTY_ID));
        doReturn(member).when(parties).hasMember(eq(PARTY_ID), eq(MEMBER_ID));

        /*
         * test
         */
        boolean result = partyAccessService.hasMemberOnParty(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isEqualTo(member);
    }

    @DisplayName("hasMemberOnParty - 없는 방이면 PARTY_NOT_FOUND 이고 멤버 조회를 하지 않는다")
    @Test
    void hasMemberOnParty_partyNotFound() {

        /*
         * settings
         */
        doReturn(false).when(parties).existsById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.hasMemberOnParty(PARTY_ID, MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
        verify(parties, never()).hasMember(anyLong(), anyLong());
    }

    // ───────────────────────── isRidingMember ─────────────────────────

    @DisplayName("isRidingMember - 멤버이고 IN_RIDE 면 true")
    @Test
    void isRidingMember() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.IN_RIDE, DRIVER_ID, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isRidingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isTrue();
    }

    @DisplayName("isRidingMember - IN_RIDE 여도 멤버가 아니면 false")
    @Test
    void isRidingMember_notMember() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.IN_RIDE, DRIVER_ID, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isRidingMember(PARTY_ID, STRANGER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("isRidingMember - 멤버여도 IN_RIDE 가 아니면 false")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = "IN_RIDE", mode = EnumSource.Mode.EXCLUDE)
    void isRidingMember_otherStatus(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, DRIVER_ID, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isRidingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("isRidingMember - 없는 방이면 예외 없이 false")
    @Test
    void isRidingMember_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isRidingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    // ───────────────────────── isOnboardingMember ─────────────────────────

    @DisplayName("isOnboardingMember - 멤버이고 진행 중인 상태면 true")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"}, mode = EnumSource.Mode.EXCLUDE)
    void isOnboardingMember(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, null, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isTrue();
    }

    @DisplayName("isOnboardingMember - 멤버여도 닫힌 방이면 false")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void isOnboardingMember_closed(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, null, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("isOnboardingMember - 진행 중이어도 멤버가 아니면 false")
    @Test
    void isOnboardingMember_notMember() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.ACTIVE, null, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, STRANGER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("isOnboardingMember - 없는 방이면 예외 없이 false")
    @Test
    void isOnboardingMember_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    // ───────────────────────── findMemberIds ─────────────────────────

    @DisplayName("findMemberIds - 합류 순 멤버 ID 를 돌려준다")
    @Test
    void findMemberIds() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.ACTIVE, null, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        List<Long> result = partyAccessService.findMemberIds(PARTY_ID);

        /*
         * verify
         */
        assertThat(result).containsExactly(HOST_ID, MEMBER_ID);
    }

    @DisplayName("findMemberIds - 없는 방이면 예외 없이 빈 목록")
    @Test
    void findMemberIds_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        List<Long> result = partyAccessService.findMemberIds(PARTY_ID);

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    // ───────────────────────── 공통 도우미 ─────────────────────────

    /** memberIds 앞쪽일수록 먼저 합류한 멤버다. 정원은 3 으로 고정 */
    private static Party party(PartyStatus status, Long taxiDriverId, Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for (int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], BASE_TIME.plusSeconds(i)));
        }

        return Party.load(PARTY_ID, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역",
                new Capacity(3), members, BASE_TIME, status, 12000, 25, "encoded-path", taxiDriverId, null, null, 0L);
    }

    /** 신호는 커밋 후 실행기에 맡긴 작업 안에서만 나가야 한다 */
    private Runnable capturedAfterCommitTask() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommitExecutor).execute(eq(SSE_TASK), captor.capture());

        return captor.getValue();
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
