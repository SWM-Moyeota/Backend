package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.api.PartyAccess;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 *     콜이 와도 응답하지 않고 그대로 5분이 지난 경우 -> MATCHING 상태에서 COMPLETED로 변경해야함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallExpirySweeper {
    private static final Duration MATCHING_TIMEOUT = Duration.ofMinutes(6);

    private final PartyAccess partyAccess;

    @Scheduled(fixedDelay = 30_000)
    public void sweep() {
        Instant cutoff = Instant.now().minus(MATCHING_TIMEOUT);

        List<Long> staleIds = partyAccess.findStaleMatchingIds(cutoff);

        for(Long partyId : staleIds) {
            try {
                partyAccess.cancelMatching(partyId);

                // TODO 승객에게 "기사를 찾지 못했어요" 알림

                log.warn("콜 만료로 매칭 복귀 partyId={}", partyId);
            } catch (IllegalArgumentException e) {
                // cancelMatching 부분에서 매칭이 된 경우
                log.info("매칭 복귀 건너뜀 partyId={}, 사유={}", partyId, e.getMessage());
            }
        }
    }
}
