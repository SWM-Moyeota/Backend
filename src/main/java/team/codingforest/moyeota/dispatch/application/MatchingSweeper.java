package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.dispatch.domain.CallCandidates;
import team.codingforest.moyeota.dispatch.domain.CallNotifier;
import team.codingforest.moyeota.matching.api.MatchingTarget;
import team.codingforest.moyeota.matching.api.PartyAccess;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 *     30초마다 매칭 중인 방을 훑어 반경을 넓혀 재탐색하고, 타임아웃이면 해산한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingSweeper {
    private final PartyAccess partyAccess;
    private final DispatchService dispatchService;
    private final CallCandidates callCandidates;
    private final CallNotifier callNotifier;

    @Scheduled(fixedDelay = 30_000)
    public void sweep() {
        for(MatchingTarget target : partyAccess.findMatchingTargets()) {
            try {
                // 시작 시각이 없는 방(컬럼 도입 이전의 옛 데이터)은 즉시 정리
                Duration elapsed = target.matchingStartedAt() == null ?
                        DispatchService.MATCHING_TIMEOUT :
                        Duration.between(target.matchingStartedAt(), Instant.now());

                if(elapsed.compareTo(DispatchService.MATCHING_TIMEOUT) >= 0) {
                    giveUp(target.partyId());
                    continue;
                }

                dispatchService.attempt(target.partyId(), dispatchService.radiusFor(elapsed));
            } catch (BusinessException | IllegalArgumentException e) {
                // 스윕 도중 수락되어 상태가 바뀐 경우 등 - 한 방의 문제가 전체 스윕을 멈추면 안 된다
                log.info("스윕 건너뜀 partyId={}, 사유={}", target.partyId(), e.getMessage());
            }
        }
    }

    private void giveUp(Long partyId) {
        partyAccess.failMatching(partyId);

        List<Long> losers = callCandidates.findAll(partyId);
        callCandidates.clear(partyId);
        callNotifier.notifyCallClosed(losers, partyId);

        // TODO 승객에게 "기사를 찾지 못해 방이 해산됐어요" 알림 (user 모듈 나오면)

        log.warn("타임아웃으로 방 해산 partyId={}", partyId);
    }
}
