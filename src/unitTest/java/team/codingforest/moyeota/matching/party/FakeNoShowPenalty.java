package team.codingforest.moyeota.matching.party;

import team.codingforest.moyeota.user.api.NoShowPenalty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *  가짜 NoShowPenalty. 제한은 restrict 로 직접 걸고, record·cancel 호출은 기록만 한다(제재 계산은 user 모듈 테스트가 본다)
 */
class FakeNoShowPenalty implements NoShowPenalty {
    record Recorded(Long userId, Long partyId, Instant meetingAt) {}
    record Cancelled(Long userId, Long partyId) {}

    final List<Recorded> recorded = new ArrayList<>();
    final List<Cancelled> cancelled = new ArrayList<>();
    private final Map<Long, Instant> restrictedUntil = new HashMap<>();

    void restrict(Long userId, Instant until) {
        restrictedUntil.put(userId, until);
    }

    @Override
    public void record(Long userId, Long partyId, Instant meetingAt, Instant now) {
        recorded.add(new Recorded(userId, partyId, meetingAt));
    }

    @Override
    public void cancel(Long userId, Long partyId, Instant now) {
        cancelled.add(new Cancelled(userId, partyId));
    }

    @Override
    public Optional<Instant> findRestrictedUntil(Long userId, Instant now) {
        return Optional.ofNullable(restrictedUntil.get(userId)).filter(until -> until.isAfter(now));
    }
}
