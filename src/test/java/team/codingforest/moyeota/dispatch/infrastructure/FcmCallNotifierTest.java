package team.codingforest.moyeota.dispatch.infrastructure;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FcmCallNotifierTest {

    private static final PartySummary 강남출발방 =
            new PartySummary(1L, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 2, 12000, 25);

    /**
     *  토큰이 등록된 기사가 한 명도 없는 DriverAccess
     */
    private static final DriverAccess 토큰없음 = new DriverAccess() {
        @Override
        public boolean canReceiveCalls(Long driverId) { return true; }

        @Override
        public Map<Long, String> findFcmTokens(List<Long> driverIds) { return Map.of(); }

        @Override
        public java.util.Optional<team.codingforest.moyeota.driver.api.DriverSummary> findSummary(Long driverId) { return java.util.Optional.empty(); }
    };

    // FirebaseMessaging 자리에 null을 주입 - 가드를 통과해 전송을 시도하면 NPE로 실패한다

    @Test
    void 토큰이_없으면_전송을_시도하지_않는다() {
        FcmCallNotifier notifier = new FcmCallNotifier(null, 토큰없음);

        assertThatCode(() -> notifier.notifyCall(List.of(1L, 2L), 강남출발방))
                .doesNotThrowAnyException();
    }

    @Test
    void 콜_마감도_토큰이_없으면_전송을_시도하지_않는다() {
        FcmCallNotifier notifier = new FcmCallNotifier(null, 토큰없음);

        assertThatCode(() -> notifier.notifyCallClosed(List.of(1L, 2L), 1L))
                .doesNotThrowAnyException();
    }
}
