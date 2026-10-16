package team.codingforest.moyeota.matching.sse.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.party.PartyJpaTest;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyVersion;
import team.codingforest.moyeota.matching.party.domain.Radius;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class PartySseHeartbeatTest {
    private final PartyJpaTest parties = new PartyJpaTest();
    private final RecordingRegistry registry = new RecordingRegistry();
    private final PartySseHeartbeat heartbeat = new PartySseHeartbeat(registry, parties, new ObjectMapper());

    @Test
    void 열린_방에는_DB_의_version_을_heartbeat_로_보낸다() {
        Party party = openParty();
        party.join(2L);
        Long id = parties.save(party).getId();
        registry.ids.add(id);

        heartbeat.beat();

        assertThat(registry.calls).containsExactly("heartbeat:" + id + ":{\"version\":1}");
    }

    @Test
    void DB_에서_이미_닫힌_방은_closed_를_보내고_연결을_정리한다() {
        Party party = openParty();
        party.leave(1L);
        Long id = parties.save(party).getId();
        registry.ids.add(id);

        heartbeat.beat();

        assertThat(registry.calls).containsExactly(
                "closeAll:" + id + ":{\"partyId\":" + id + ",\"status\":\"CANCELED\",\"version\":1}:heartbeat-closed");
    }

    @Test
    void DB_에_없는_방에는_아무것도_보내지_않는다() {
        registry.ids.add(999L);

        heartbeat.beat();

        assertThat(registry.calls).isEmpty();
    }

    @Test
    void 버전_조회가_실패하면_이번_회차는_아무것도_보내지_않는다() {
        Long id = parties.save(openParty()).getId();
        registry.ids.add(id);
        PartySseHeartbeat failing = new PartySseHeartbeat(registry, new PartyJpaTest() {
            @Override
            public List<PartyVersion> findVersions(Collection<Long> ids) {
                throw new IllegalStateException("db down");
            }
        }, new ObjectMapper());

        failing.beat();

        assertThat(registry.calls).isEmpty();
    }

    @Test
    void 방이_많으면_나눠서_조회한다() {
        List<Integer> chunks = new ArrayList<>();
        PartySseHeartbeat counting = new PartySseHeartbeat(registry, new PartyJpaTest() {
            @Override
            public List<PartyVersion> findVersions(Collection<Long> ids) {
                chunks.add(ids.size());
                return List.of();
            }
        }, new ObjectMapper());
        LongStream.rangeClosed(1, PartySseHeartbeat.CHUNK + 1).forEach(registry.ids::add);

        counting.beat();

        assertThat(chunks).containsExactly(PartySseHeartbeat.CHUNK, 1);
    }

    @Test
    void 연결이_없으면_조회하지_않는다() {
        List<Integer> chunks = new ArrayList<>();
        PartySseHeartbeat counting = new PartySseHeartbeat(registry, new PartyJpaTest() {
            @Override
            public List<PartyVersion> findVersions(Collection<Long> ids) {
                chunks.add(ids.size());
                return List.of();
            }
        }, new ObjectMapper());

        counting.beat();

        assertThat(chunks).isEmpty();
    }

    private static Party openParty() {
        return Party.open(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
    }

    static class RecordingRegistry extends PartySseRegistry {
        final Set<Long> ids = new LinkedHashSet<>();
        final List<String> calls = new ArrayList<>();

        RecordingRegistry() {
            super(new PartySseMetrics(new SimpleMeterRegistry()));
        }

        @Override
        public Set<Long> partyIds() {
            return ids;
        }

        @Override
        public void sendHeartbeat(Long partyId, String data) {
            calls.add("heartbeat:" + partyId + ":" + data);
        }

        @Override
        public void closeAll(Long partyId, String data, String reason) {
            calls.add("closeAll:" + partyId + ":" + data + ":" + reason);
        }
    }
}
