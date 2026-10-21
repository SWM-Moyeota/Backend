package team.codingforest.moyeota.matching.sse.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import team.codingforest.moyeota.matching.sse.dto.PartySseMessage;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  Redis 채널 수신 → 로컬 레지스트리. 메시지는 PartySseMessage JSON 이다.
 *  나간 사람이 있으면 그 연결부터 닫고 알린다 - 순서가 바뀌면 나간 사람도 자기 퇴장 이벤트를 받는다.
 */
class PartySseChannelTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingRegistry registry = new RecordingRegistry();
    private final PartySseChannel channel = new PartySseChannel(objectMapper, registry);

    @Test
    void 참여_신호는_그_방에_데이터와_함께_알린다() {
        channel.onMessage(msg(new PartySseMessage(42L, "member-joined", "{\"status\":\"ACTIVE\"}", null)), null);

        assertThat(registry.calls).containsExactly("notify:42:member-joined:{\"status\":\"ACTIVE\"}");
    }

    @Test
    void 퇴장_신호는_나간_사람의_연결을_먼저_닫고_남은_사람에게_알린다() {
        channel.onMessage(msg(new PartySseMessage(42L, "member-left", "{}", 7L)), null);

        assertThat(registry.calls).containsExactly("closeMember:42:7", "notify:42:member-left:{}");
    }

    @Test
    void closed_신호는_따로_알리지_않고_데이터와_함께_방을_닫는다() {
        channel.onMessage(msg(new PartySseMessage(42L, "closed", "42", null)), null);

        assertThat(registry.calls).containsExactly("closeAll:42:42");
    }

    @Test
    void 참여_신호는_연결을_끊지_않는다() {
        channel.onMessage(msg(new PartySseMessage(42L, "member-joined", "{}", null)), null);

        assertThat(registry.calls).noneMatch(c -> c.startsWith("close"));
    }

    @Test
    void JSON_이_아닌_메시지는_구독_스레드를_죽이지_않고_무시한다() {
        // 예외가 새면 RedisMessageListenerContainer 의 리스너 스레드가 멈춰 그 뒤 신호를 영영 못 받는다
        channel.onMessage(raw("42:changed"), null);

        assertThat(registry.calls).isEmpty();
    }

    private Message msg(PartySseMessage message) {
        return raw(objectMapper.writeValueAsString(message));
    }

    private static Message raw(String body) {
        return new DefaultMessage(PartySseChannel.TOPIC.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }

    /** 실제 emitter 대신 호출 순서만 기록한다 */
    static class RecordingRegistry extends PartySseRegistry {
        final List<String> calls = new ArrayList<>();

        RecordingRegistry() {
            super(new PartySseMetrics(new SimpleMeterRegistry()));
        }

        @Override
        public void notify(Long partyId, String event, String data) {
            calls.add("notify:" + partyId + ":" + event + ":" + data);
        }

        @Override
        public void closeAll(Long partyId, String data) {
            calls.add("closeAll:" + partyId + ":" + data);
        }

        @Override
        public void closeMember(Long partyId, Long memberId) {
            calls.add("closeMember:" + partyId + ":" + memberId);
        }
    }
}
