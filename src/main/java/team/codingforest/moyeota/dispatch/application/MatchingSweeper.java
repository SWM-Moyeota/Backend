package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.dispatch.domain.CallCandidates;
import team.codingforest.moyeota.dispatch.domain.CallNotifier;
import team.codingforest.moyeota.dispatch.domain.MatchingTimers;
import team.codingforest.moyeota.matching.api.PartyAccess;

import java.time.Duration;
import java.util.List;

/**
 *     30초마다 매칭 중인 방을 훑어 반경을 넓혀 재탐색하고, 타임아웃이면 접는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingSweeper {
    private final PartyAccess partyAccess;
    private final DispatchService dispatchService;
    private final MatchingTimers matchingTimers;
    private final CallCandidates callCandidates;
    private final CallNotifier callNotifier;

    @Scheduled(fixedDelay = 30_000)
    public void sweep() {
        for(Long partyId : partyAccess.findMatchingIds()) {
            try {
                Duration elapsed = matchingTimers.elapsed(partyId)
                        .orElse(DispatchService.MATCHING_TIMEOUT);   // 타이머 유실(재시작 등) 시 즉시 타임아웃 처리

                if(elapsed.compareTo(DispatchService.MATCHING_TIMEOUT) >= 0) {
                    giveUp(partyId);
                    continue;
                }

                dispatchService.attempt(partyId, dispatchService.radiusFor(elapsed));
            } catch (IllegalArgumentException e) {
                // 스윕 도중 수락되어 상태가 바뀐 경우 등
                log.info("스윕 건너뜀 partyId={}, 사유={}", partyId, e.getMessage());
            }
        }
    }

    private void giveUp(Long partyId) {
        partyAccess.cancelMatching(partyId);

        List<Long> losers = callCandidates.findAll(partyId);
        callCandidates.clear(partyId);
        matchingTimers.clear(partyId);
        callNotifier.notifyCallClosed(losers, partyId);

        // TODO 승객에게 "기사를 찾지 못했어요" 알림

        log.warn("타임아웃으로 매칭 복귀 partyId={}", partyId);
    }
}
