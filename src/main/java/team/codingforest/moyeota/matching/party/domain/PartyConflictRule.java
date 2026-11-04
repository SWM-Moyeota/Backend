package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Duration;
import java.time.Instant;

public final class PartyConflictRule {
    private static final Duration CONFLICT_WINDOW = Duration.ofHours(2);

    private PartyConflictRule() {}

    public static void check(Parties parties, Long memberId, Instant departure) {
        checkNotInRealtime(parties, memberId);
        checkTimeConflict(parties, memberId, departure);
    }

    public static void checkNotInRealtime(Parties parties, Long memberId) {
        if(parties.existsOngoingRealtimeByMemberId(memberId)) throw new BusinessException(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
    }

    public static void checkTimeConflict(Parties parties, Long memberId, Instant departure) {
        if(parties.existsOngoingReservationBetween(memberId, departure.minus(CONFLICT_WINDOW), departure.plus(CONFLICT_WINDOW))) {
            throw new BusinessException(MatchingErrorCode.PARTY_TIME_CONFLICT);
        }
    }
}
