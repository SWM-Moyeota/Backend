package team.codingforest.moyeota.dispatch.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.dispatch.application.dto.DriverLocationResponse;
import team.codingforest.moyeota.dispatch.domain.DriverPosition;
import team.codingforest.moyeota.dispatch.domain.PassengerNotifier;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RideServiceTest {

    private static final Long 방번호 = 1L;
    private static final Long 기사 = 9L;
    private static final Long 다른기사 = 10L;
    private static final PartySummary 강남출발방 =
            new PartySummary(방번호, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 2, 12000, 25, null);

    private static final Long 승객 = 5L;

    private FakePartyAccess partyAccess;
    private RecordingPassengerNotifier passengers;
    private FakeDriverLocations locations;
    private RideService service;

    @BeforeEach
    void setUp() {
        partyAccess = new FakePartyAccess(강남출발방);
        passengers = new RecordingPassengerNotifier();
        locations = new FakeDriverLocations();
        service = new RideService(partyAccess, passengers, locations);
    }

    private void 기사배정됨() {
        partyAccess.assignedDriverId = 기사;
    }

    // ───────────────────────── 도착 통보 ─────────────────────────

    @Test
    void 배정된_기사가_도착을_통보하면_승객에게_알림이_간다() {
        기사배정됨();

        service.arrive(방번호, 기사);

        assertThat(passengers.arrivedParties).containsExactly(방번호);
    }

    @Test
    void 배정되지_않은_기사는_도착을_통보할_수_없다() {
        // URL 조작 - 남의 방 승객들에게 "기사님 도착" 알림을 쏘는 스팸 차단
        기사배정됨();

        assertThatThrownBy(() -> service.arrive(방번호, 다른기사))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(passengers.arrivedParties).isEmpty();
    }

    @Test
    void 기사_배정_전에는_도착을_통보할_수_없다() {
        // 매칭 중인 방 - 배정 전이라 도착이 성립하지 않는다 (NPE가 아니라 도메인 예외)
        assertThatThrownBy(() -> service.arrive(방번호, 기사))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(passengers.arrivedParties).isEmpty();
    }

    @Test
    void 운행이_시작된_방에는_도착을_통보할_수_없다() {
        // 이미 태우고 달리는 중 - 뒤늦은/중복된 "기사님 도착" 알림은 승객을 혼란시킨다
        기사배정됨();
        service.board(방번호, 기사);

        assertThatThrownBy(() -> service.arrive(방번호, 기사))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(passengers.arrivedParties).isEmpty();
    }

    @Test
    void 도착_통보_이중_탭이면_알림도_두_번_간다() {
        // 현재 정책 문서화: 도착 통보는 멱등하지 않다 - 스팸이 문제되면 1회 제한 follow-up
        기사배정됨();

        service.arrive(방번호, 기사);
        service.arrive(방번호, 기사);

        assertThat(passengers.arrivedParties).containsExactly(방번호, 방번호);
    }

    // ───────────────────────── 탑승 / 운행 종료 ─────────────────────────

    @Test
    void 배정된_기사가_탑승을_확정하면_운행이_시작된다() {
        기사배정됨();

        service.board(방번호, 기사);

        assertThat(partyAccess.rideStarted).isTrue();
    }

    @Test
    void 배정되지_않은_기사는_탑승을_확정할_수_없다() {
        기사배정됨();

        assertThatThrownBy(() -> service.board(방번호, 다른기사))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(partyAccess.rideStarted).isFalse();
    }

    @Test
    void 운행을_종료하면_요금이_전달된다() {
        기사배정됨();
        service.board(방번호, 기사);

        service.complete(방번호, 기사, 15000);

        assertThat(partyAccess.completedFare).isEqualTo(15000);
    }

    @Test
    void 탑승_확정_전에는_운행을_종료할_수_없다() {
        // 도착 통보만 하고 board 없이 complete를 누른 경우 - 순서 강제
        기사배정됨();
        service.arrive(방번호, 기사);

        assertThatThrownBy(() -> service.complete(방번호, 기사, 15000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 도착_통보_없이도_탑승할_수_있다() {
        // 승객이 길에서 바로 잡아탄 경우 - arrive는 선택 단계여야 한다
        기사배정됨();

        assertThatCode(() -> service.board(방번호, 기사)).doesNotThrowAnyException();
    }

    // ───────────────────────── 기사 위치 조회 (승객용) ─────────────────────────

    @Test
    void 승객이_오는_기사의_위치를_조회한다() {
        기사배정됨();
        partyAccess.members.add(승객);
        locations.position = new DriverPosition(37.4979, 127.0276);

        DriverLocationResponse response = service.driverLocation(방번호, 승객);

        // 위경도가 뒤바뀌면 지도에서 기사가 바다에 뜬다 - 필드별로 정확히 검증
        assertThat(response.latitude()).isEqualTo(37.4979);
        assertThat(response.longitude()).isEqualTo(127.0276);
    }

    @Test
    void 방에_참여하지_않은_사람은_기사_위치를_조회할_수_없다() {
        // partyId를 바꿔가며 남의 방 기사 위치를 훔쳐보는 시나리오 차단
        기사배정됨();
        locations.position = new DriverPosition(37.4979, 127.0276);

        assertThatThrownBy(() -> service.driverLocation(방번호, 승객))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 방에 참여하고 있지 않습니다.");
    }

    @Test
    void 기사_배정_전에는_위치를_조회할_수_없다() {
        // 매칭 중인 방 - driverId가 null이라 NPE가 아니라 도메인 예외가 나야 한다
        partyAccess.members.add(승객);

        assertThatThrownBy(() -> service.driverLocation(방번호, 승객))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("아직 기사가 배정되지 않았습니다.");
    }

    @Test
    void 기사_위치_신호가_없으면_확인할_수_없다는_안내가_나간다() {
        // 기사 앱이 죽어 heartbeat가 만료된 경우 - 낡은 위치를 실시간처럼 보여주면 안 된다
        기사배정됨();
        partyAccess.members.add(승객);

        assertThatThrownBy(() -> service.driverLocation(방번호, 승객))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 기사님의 위치를 확인할 수 없습니다.");
    }

    /** 어느 방에 도착 알림이 갔는지 기록하는 가짜 */
    static class RecordingPassengerNotifier implements PassengerNotifier {
        final List<Long> arrivedParties = new ArrayList<>();

        @Override
        public void notifyDriverArrived(Long partyId) {
            arrivedParties.add(partyId);
        }
    }
}
