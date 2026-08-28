package team.codingforest.moyeota.dispatch.domain;

import java.time.Duration;
import java.util.Optional;

public interface MatchingTimers {
    void start(Long partyId);
    Optional<Duration> elapsed(Long partyId);
    void clear(Long partyId);
}
