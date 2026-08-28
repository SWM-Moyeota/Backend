package team.codingforest.moyeota.dispatch.application;

import team.codingforest.moyeota.dispatch.domain.MatchingTimers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *  경과 시간을 마음대로 정해줄 수 있는 가짜 타이머
 */
class FakeMatchingTimers implements MatchingTimers {
    final Map<Long, Duration> elapsedByParty = new HashMap<>();
    final List<Long> started = new ArrayList<>();
    final List<Long> cleared = new ArrayList<>();

    @Override
    public void start(Long partyId) {
        started.add(partyId);
        elapsedByParty.put(partyId, Duration.ZERO);
    }

    @Override
    public Optional<Duration> elapsed(Long partyId) {
        return Optional.ofNullable(elapsedByParty.get(partyId));
    }

    @Override
    public void clear(Long partyId) {
        cleared.add(partyId);
        elapsedByParty.remove(partyId);
    }
}
