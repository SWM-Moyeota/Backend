package team.codingforest.moyeota.dispatch.infrastructure;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.api.MatchingTarget;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartyChatSummary;
import team.codingforest.moyeota.matching.api.PartySummary;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FcmPassengerNotifierTest {

    /** 방 구성원만 돌려주는 PartyAccess - 나머지는 이 테스트에서 쓰이지 않는다 */
    private static PartyAccess 구성원(Long... memberIds) {
        return new PartyAccess() {
            @Override public List<Long> findMemberIds(Long partyId) { return List.of(memberIds); }
            @Override public Optional<PartySummary> findSummary(Long partyId) { return Optional.empty(); }
            @Override public void assignDriver(Long partyId, Long driverId) {}
            @Override public void failMatching(Long partyId) {}
            @Override public List<MatchingTarget> findMatchingTargets() { return List.of(); }
            @Override public void startRide(Long partyId, Long driverId) {}
            @Override public void completeRide(Long partyId, Long driverId, int fare) {}
            @Override public boolean isAwaitingPickup(Long partyId, Long driverId) { return false; }
            @Override public PartyChatSummary findChatSummary(Long partyId) { return null; }
            @Override public boolean hasOngoingRide(Long driverId) { return false; }
            @Override public boolean hasMemberOnParty(Long memberId, Long partyId) { return false; }
            @Override public boolean isRidingMember(Long partyId, Long memberId) { return false; }
        };
    }

    /** 조회된 userId 를 기록하고 토큰은 하나도 돌려주지 않는 UserAccess */
    private static class 토큰없음 implements UserAccess {
        final List<Long> 조회된유저 = new ArrayList<>();

        @Override
        public Map<Long, String> findFcmTokens(List<Long> userIds) {
            조회된유저.addAll(userIds);
            return Map.of();
        }

        @Override
        public Map<Long, MemberSummary> findMemberSummaries(List<Long> userIds) { return Map.of(); }

        @Override
        public java.util.Optional<String> findNickname(Long userId) { return java.util.Optional.empty(); }
    }

    // FirebaseMessaging 자리에 null을 주입 - 가드를 통과해 전송을 시도하면 NPE로 실패한다

    @Test
    void 토큰이_없으면_전송을_시도하지_않는다() {
        FcmPassengerNotifier notifier = new FcmPassengerNotifier(null, 구성원(1L, 2L), new 토큰없음());

        assertThatCode(() -> notifier.notifyDriverArrived(10L)).doesNotThrowAnyException();
    }

    @Test
    void 방_구성원_명단으로_토큰을_조회한다() {
        // 기사·다른 방 승객이 아니라 이 방 구성원의 토큰만 찾아야 한다
        토큰없음 userAccess = new 토큰없음();
        FcmPassengerNotifier notifier = new FcmPassengerNotifier(null, 구성원(1L, 2L, 3L), userAccess);

        notifier.notifyDriverArrived(10L);

        assertThat(userAccess.조회된유저).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 구성원이_없는_방은_전송을_시도하지_않는다() {
        // 방이 없거나 비어 있어도 도착 API 자체가 실패해선 안 된다
        FcmPassengerNotifier notifier = new FcmPassengerNotifier(null, 구성원(), new 토큰없음());

        assertThatCode(() -> notifier.notifyDriverArrived(10L)).doesNotThrowAnyException();
    }
}
