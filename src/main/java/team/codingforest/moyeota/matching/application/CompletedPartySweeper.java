package team.codingforest.moyeota.matching.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.Parties;

import java.time.Duration;
import java.time.Instant;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "false")
@Slf4j
@Component
@RequiredArgsConstructor
public class CompletedPartySweeper {
    private static final Duration COMPLETED_TTL = Duration.ofMinutes(30);

    private final Parties parties;
    private final PartyApplicationService partyService;

    @Scheduled(fixedDelay = 600_000)
    public void sweep() {
        Instant cutoff = Instant.now().minus(COMPLETED_TTL);

        for(Long partyId : parties.findCompletedBefore(cutoff)) {
            try {
                partyService.expire(partyId);
            } catch (BusinessException e) {
                log.info("자동 종료 건너 뜀 partyId={}, 사유={}", partyId, e.getMessage());
            }
        }
    }
}
