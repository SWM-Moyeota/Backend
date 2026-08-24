package team.codingforest.moyeota.dispatch.application;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CallExpirySweeperTest {

    @Test
    void 오래된_미배정_MATCHING_방들을_전부_되돌린다() {
        FakePartyAccess partyAccess = new FakePartyAccess(List.of(1L, 2L), Set.of());
        CallExpirySweeper sweeper = new CallExpirySweeper(partyAccess);

        sweeper.sweep();

        assertThat(partyAccess.matchingCanceled).containsExactly(1L, 2L);
    }

    @Test
    void 스캔_도중_기사가_배정된_방은_건너뛰고_나머지는_계속_처리한다() {
        // 1번 방은 스캔과 수락이 겹쳐 cancelMatching 이 예외를 던지는 상황
        FakePartyAccess partyAccess = new FakePartyAccess(List.of(1L, 2L), Set.of(1L));
        CallExpirySweeper sweeper = new CallExpirySweeper(partyAccess);

        assertThatCode(sweeper::sweep).doesNotThrowAnyException();   // 예외가 스케줄러로 새면 안 됨

        assertThat(partyAccess.matchingCanceled).containsExactly(2L);
    }

    @Test
    void 갇힌_방이_없으면_아무_일도_하지_않는다() {
        FakePartyAccess partyAccess = new FakePartyAccess(List.of(), Set.of());
        CallExpirySweeper sweeper = new CallExpirySweeper(partyAccess);

        sweeper.sweep();

        assertThat(partyAccess.matchingCanceled).isEmpty();
    }

    /**
     *  stale 목록을 정해주고, 특정 방은 "이미 배정됨" 으로 되돌림을 거부하는 가짜
     */
    static class FakePartyAccess implements PartyAccess {
        private final List<Long> staleIds;
        private final Set<Long> assignedWhileSweeping;
        List<Long> matchingCanceled = new ArrayList<>();

        FakePartyAccess(List<Long> staleIds, Set<Long> assignedWhileSweeping) {
            this.staleIds = staleIds;
            this.assignedWhileSweeping = assignedWhileSweeping;
        }

        @Override
        public Optional<PartySummary> findSummary(Long partyId) {
            return Optional.empty();
        }

        @Override
        public void assignDriver(Long partyId, Long driverId) {}

        @Override
        public void cancelMatching(Long partyId) {
            if(assignedWhileSweeping.contains(partyId)) throw new IllegalArgumentException("이미 기사가 배정된 방입니다.");
            matchingCanceled.add(partyId);
        }

        @Override
        public List<Long> findStaleMatchingIds(Instant cutoff) {
            return staleIds;
        }
    }
}
