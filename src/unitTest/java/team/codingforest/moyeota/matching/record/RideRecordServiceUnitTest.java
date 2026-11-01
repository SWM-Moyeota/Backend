package team.codingforest.moyeota.matching.record;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.record.domain.RideRecords;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RideRecordServiceUnitTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long CURSOR = 500L;
    private static final UUID MEMBER_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant BASE_TIME = Instant.parse("2026-10-01T00:00:00Z");

    @Mock
    private RideRecords rideRecords;
    @Mock
    private UserAccess userAccess;

    @InjectMocks
    private RideRecordService rideRecordService;

    @DisplayName("findMyRides - 첫 페이지는 size+1 개를 조회하고, 다음이 없으면 커서 없이 전부 돌려준다")
    @Test
    void findMyRides() {

        /*
         * settings
         */
        RideRecord record = droppedOff(10L, 15000);

        doReturn(List.of(record)).when(rideRecords).findDroppedOffByMember(eq(MEMBER_ID), eq(21));
        doReturn(Map.of(10L, List.of(MEMBER_ID, OTHER_MEMBER_ID))).when(rideRecords).findMemberIds(eq(List.of(10L)));
        doReturn(Map.of(MEMBER_ID, new MemberSummary(MEMBER_PUBLIC_ID, "나", "me.png", null),
                OTHER_MEMBER_ID, new MemberSummary(OTHER_PUBLIC_ID, "동승자", "other.png", null)))
                .when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID, OTHER_MEMBER_ID)));

        /*
         * test
         */
        RideRecordResponse result = rideRecordService.findMyRides(MEMBER_ID, null, 20);

        /*
         * verify
         */
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.rides()).hasSize(1);

        RideRecordResponse.RideRecordItem item = result.rides().getFirst();
        assertThat(item.partyId()).isEqualTo(10L);
        assertThat(item.departure()).isEqualTo("강남역");
        assertThat(item.destination()).isEqualTo("판교역");
        assertThat(item.pickupTime()).isEqualTo(BASE_TIME);
        assertThat(item.dropoffTime()).isEqualTo(BASE_TIME.plusSeconds(600));
        assertThat(item.totalFare()).isEqualTo(15000);
        assertThat(item.farePerPerson()).isEqualTo(7500);
        assertThat(item.currentMembers()).isEqualTo(2);
        assertThat(item.members()).containsExactly(
                new RideRecordResponse.RideRecordMember(MEMBER_PUBLIC_ID, "나", "me.png"),
                new RideRecordResponse.RideRecordMember(OTHER_PUBLIC_ID, "동승자", "other.png"));
        verify(rideRecords, never()).findById(any());
    }

    @DisplayName("findMyRides - size 보다 많이 조회되면 잘라 다음 페이지가 있고 커서는 이 페이지 마지막 기록이다")
    @Test
    void findMyRides_hasNext() {

        /*
         * settings
         */
        RideRecord first = droppedOff(30L, 9000);
        RideRecord second = droppedOff(20L, 9000);
        RideRecord beyond = droppedOff(10L, 9000);

        doReturn(List.of(first, second, beyond)).when(rideRecords).findDroppedOffByMember(eq(MEMBER_ID), eq(3));
        doReturn(Map.of(30L, List.of(MEMBER_ID), 20L, List.of(MEMBER_ID))).when(rideRecords).findMemberIds(eq(List.of(30L, 20L)));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));

        /*
         * test
         */
        RideRecordResponse result = rideRecordService.findMyRides(MEMBER_ID, null, 2);

        /*
         * verify
         */
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(20L);
        assertThat(result.rides()).extracting(RideRecordResponse.RideRecordItem::partyId).containsExactly(30L, 20L);
    }

    @DisplayName("findMyRides - 조회 수가 size 와 정확히 같으면 다음 페이지가 없다")
    @Test
    void findMyRides_exactlySize() {

        /*
         * settings
         */
        doReturn(List.of(droppedOff(30L, 9000), droppedOff(20L, 9000))).when(rideRecords).findDroppedOffByMember(eq(MEMBER_ID), eq(3));
        doReturn(Map.of(30L, List.of(MEMBER_ID), 20L, List.of(MEMBER_ID))).when(rideRecords).findMemberIds(eq(List.of(30L, 20L)));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));

        /*
         * test
         */
        RideRecordResponse result = rideRecordService.findMyRides(MEMBER_ID, null, 2);

        /*
         * verify
         */
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.rides()).hasSize(2);
    }

    @DisplayName("findMyRides - 커서가 있으면 그 기록의 하차 시각과 ID 이전을 조회한다")
    @Test
    void findMyRides_withCursor() {

        /*
         * settings
         */
        Instant cursorDropoff = BASE_TIME.plusSeconds(600);
        RideRecord cursorRecord = RideRecord.load(CURSOR, 9L, "강남역", "판교역", BASE_TIME, cursorDropoff, 9000);

        doReturn(Optional.of(cursorRecord)).when(rideRecords).findById(eq(CURSOR));
        doReturn(List.of(droppedOff(10L, 9000))).when(rideRecords).findDroppedOffByMemberBefore(eq(MEMBER_ID), eq(cursorDropoff), eq(CURSOR), eq(21));
        doReturn(Map.of(10L, List.of(MEMBER_ID))).when(rideRecords).findMemberIds(eq(List.of(10L)));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));

        /*
         * test
         */
        RideRecordResponse result = rideRecordService.findMyRides(MEMBER_ID, CURSOR, 20);

        /*
         * verify
         */
        assertThat(result.rides()).extracting(RideRecordResponse.RideRecordItem::partyId).containsExactly(10L);
        verify(rideRecords, never()).findDroppedOffByMember(any(), anyInt());
    }

    @DisplayName("findMyRides - 기록이 없으면 빈 페이지이고 멤버·유저 조회를 하지 않는다")
    @Test
    void findMyRides_empty() {

        /*
         * settings
         */
        doReturn(List.of()).when(rideRecords).findDroppedOffByMember(eq(MEMBER_ID), eq(21));

        /*
         * test
         */
        RideRecordResponse result = rideRecordService.findMyRides(MEMBER_ID, null, 20);

        /*
         * verify
         */
        assertThat(result.rides()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
        verify(rideRecords, never()).findMemberIds(any());
        verifyNoInteractions(userAccess);
    }

    @DisplayName("findMyRides - 경계값 size 1·50 은 허용하고 size+1 로 조회한다")
    @ParameterizedTest(name = "size={0}")
    @ValueSource(ints = {1, 50})
    void findMyRides_boundarySize(int size) {

        /*
         * settings
         */
        doReturn(List.of()).when(rideRecords).findDroppedOffByMember(eq(MEMBER_ID), eq(size + 1));

        /*
         * test
         */
        RideRecordResponse result = rideRecordService.findMyRides(MEMBER_ID, null, size);

        /*
         * verify
         */
        assertThat(result.rides()).isEmpty();
    }

    @DisplayName("findMyRides - size 가 1~50 밖이면 INVALID_PAGE_SIZE 이고 조회하지 않는다")
    @ParameterizedTest(name = "size={0}")
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 51, Integer.MAX_VALUE})
    void findMyRides_invalidSize(int size) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideRecordService.findMyRides(MEMBER_ID, null, size));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.INVALID_PAGE_SIZE);
        verifyNoInteractions(rideRecords, userAccess);
    }

    @DisplayName("findMyRides - 없는 기록을 커서로 주면 INVALID_CURSOR")
    @Test
    void findMyRides_cursorNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(rideRecords).findById(eq(CURSOR));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideRecordService.findMyRides(MEMBER_ID, CURSOR, 20));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.INVALID_CURSOR);
        verify(rideRecords, never()).findDroppedOffByMemberBefore(any(), any(), any(), anyInt());
        verifyNoInteractions(userAccess);
    }

    @DisplayName("findMyRides - 아직 하차 전인 기록을 커서로 주면 INVALID_CURSOR")
    @Test
    void findMyRides_cursorNotDroppedOff() {

        /*
         * settings
         */
        doReturn(Optional.of(RideRecord.load(CURSOR, 9L, "강남역", "판교역", BASE_TIME, null, null))).when(rideRecords).findById(eq(CURSOR));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> rideRecordService.findMyRides(MEMBER_ID, CURSOR, 20));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.INVALID_CURSOR);
        verify(rideRecords, never()).findDroppedOffByMemberBefore(any(), any(), any(), anyInt());
        verifyNoInteractions(userAccess);
    }

    @DisplayName("findMyRides - 커서 다음 페이지가 비면 빈 페이지이고 멤버·유저 조회를 하지 않는다")
    @Test
    void findMyRides_withCursorEmpty() {

        /*
         * settings
         */
        Instant cursorDropoff = BASE_TIME.plusSeconds(600);

        doReturn(Optional.of(RideRecord.load(CURSOR, 9L, "강남역", "판교역", BASE_TIME, cursorDropoff, 9000))).when(rideRecords).findById(eq(CURSOR));
        doReturn(List.of()).when(rideRecords).findDroppedOffByMemberBefore(eq(MEMBER_ID), eq(cursorDropoff), eq(CURSOR), eq(21));

        /*
         * test
         */
        RideRecordResponse result = rideRecordService.findMyRides(MEMBER_ID, CURSOR, 20);

        /*
         * verify
         */
        assertThat(result.rides()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        verify(rideRecords, never()).findMemberIds(any());
        verifyNoInteractions(userAccess);
    }

    @DisplayName("findMyRides - 여러 기록에 겹치는 멤버는 ID 를 중복 없이 모아 유저 조회한다")
    @Test
    void findMyRides_distinctMembers() {

        /*
         * settings
         */
        doReturn(List.of(droppedOff(30L, 9000), droppedOff(20L, 9000))).when(rideRecords).findDroppedOffByMember(eq(MEMBER_ID), eq(21));
        doReturn(Map.of(30L, List.of(MEMBER_ID), 20L, List.of(MEMBER_ID))).when(rideRecords).findMemberIds(eq(List.of(30L, 20L)));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));

        /*
         * test
         */
        rideRecordService.findMyRides(MEMBER_ID, null, 20);

        /*
         * verify
         */
        verify(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));
    }

    @DisplayName("findMyRides - 1차 모드 기록은 요금이 null 이라 1인 요금도 null 이다")
    @Test
    void findMyRides_noFare() {

        /*
         * settings
         */
        RideRecord withoutDriver = RideRecord.load(10L, null, "강남역", "판교역", null, BASE_TIME, null);

        doReturn(List.of(withoutDriver)).when(rideRecords).findDroppedOffByMember(eq(MEMBER_ID), eq(21));
        doReturn(Map.of(10L, List.of(MEMBER_ID, OTHER_MEMBER_ID))).when(rideRecords).findMemberIds(eq(List.of(10L)));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID, OTHER_MEMBER_ID)));

        /*
         * test
         */
        RideRecordResponse result = rideRecordService.findMyRides(MEMBER_ID, null, 20);

        /*
         * verify
         */
        RideRecordResponse.RideRecordItem item = result.rides().getFirst();
        assertThat(item.pickupTime()).isNull();
        assertThat(item.totalFare()).isNull();
        assertThat(item.farePerPerson()).isNull();
        assertThat(item.currentMembers()).isEqualTo(2);
    }

    @DisplayName("findMyRides - 1인 요금은 정수 나눗셈으로 나머지를 버린다")
    @Test
    void findMyRides_farePerPersonTruncated() {

        /*
         * settings
         */
        doReturn(List.of(droppedOff(10L, 10000))).when(rideRecords).findDroppedOffByMember(eq(MEMBER_ID), eq(21));
        doReturn(Map.of(10L, List.of(MEMBER_ID, OTHER_MEMBER_ID, 3L))).when(rideRecords).findMemberIds(eq(List.of(10L)));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID, OTHER_MEMBER_ID, 3L)));

        /*
         * test
         */
        RideRecordResponse result = rideRecordService.findMyRides(MEMBER_ID, null, 20);

        /*
         * verify
         */
        assertThat(result.rides().getFirst().farePerPerson()).isEqualTo(3333);
    }

    @DisplayName("findMyRides - 유저 요약이 없는 멤버는 세 필드가 모두 null 로 나온다")
    @Test
    void findMyRides_noMemberSummary() {

        /*
         * settings
         */
        doReturn(List.of(droppedOff(10L, 9000))).when(rideRecords).findDroppedOffByMember(eq(MEMBER_ID), eq(21));
        doReturn(Map.of(10L, List.of(MEMBER_ID))).when(rideRecords).findMemberIds(eq(List.of(10L)));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of(MEMBER_ID)));

        /*
         * test
         */
        RideRecordResponse result = rideRecordService.findMyRides(MEMBER_ID, null, 20);

        /*
         * verify
         */
        assertThat(result.rides().getFirst().members()).containsExactly(new RideRecordResponse.RideRecordMember(null, null, null));
    }

    @DisplayName("findMyRides - 멤버 목록이 없는 기록은 빈 멤버·0명·1인 요금 null 로 나온다")
    @Test
    void findMyRides_noMembers() {

        /*
         * settings
         */
        doReturn(List.of(droppedOff(10L, 9000))).when(rideRecords).findDroppedOffByMember(eq(MEMBER_ID), eq(21));
        doReturn(Map.of()).when(rideRecords).findMemberIds(eq(List.of(10L)));
        doReturn(Map.of()).when(userAccess).findMemberSummaries(eq(List.of()));

        /*
         * test
         */
        RideRecordResponse result = rideRecordService.findMyRides(MEMBER_ID, null, 20);

        /*
         * verify
         */
        RideRecordResponse.RideRecordItem item = result.rides().getFirst();
        assertThat(item.members()).isEmpty();
        assertThat(item.currentMembers()).isZero();
        assertThat(item.totalFare()).isEqualTo(9000);
        assertThat(item.farePerPerson()).isNull();
        verify(userAccess).findMemberSummaries(anyList());
    }

    private static RideRecord droppedOff(Long matchId, Integer totalFare) {
        return RideRecord.load(matchId, 9L, "강남역", "판교역", BASE_TIME, BASE_TIME.plusSeconds(600), totalFare);
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
