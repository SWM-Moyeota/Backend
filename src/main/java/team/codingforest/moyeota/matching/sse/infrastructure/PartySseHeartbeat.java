package team.codingforest.moyeota.matching.sse.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.PartyVersion;
import team.codingforest.moyeota.matching.party.dto.PartyClosed;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartySseHeartbeat {
    static final int CHUNK = 500;

    private final PartySseRegistry registry;
    private final Parties parties;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 30_000, scheduler = PartySseSchedulerConfig.SCHEDULER)
    public void beat() {
        List<Long> partyIds = List.copyOf(registry.partyIds());

        for (int from = 0; from < partyIds.size(); from += CHUNK) {
            List<PartyVersion> versions;
            try {
                versions = parties.findVersions(partyIds.subList(from, Math.min(from + CHUNK, partyIds.size())));
            } catch (Exception e) {
                log.warn("SSE heartbeat 버전 조회 실패 parties={}", partyIds.size(), e);
                return;
            }

            versions.forEach(this::send);
        }
    }

    private void send(PartyVersion v) {
        if (v.status().isClosed()) {
            String data = objectMapper.writeValueAsString(PartyClosed.from(v.id(), v.status().name(), v.version()));
            registry.closeAll(v.id(), data, "heartbeat-closed");
            return;
        }

        registry.sendHeartbeat(v.id(), "{\"version\":%d}".formatted(v.version()));
    }
}
