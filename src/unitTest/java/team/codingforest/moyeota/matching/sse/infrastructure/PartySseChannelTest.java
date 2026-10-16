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
 *  closed 는 알린 뒤 이 인스턴스의 연결을 끊는다 - 순서가 바뀌면 아무도 못 받는다.
 */
class PartySseChannelTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingRegistry registry = new RecordingRegistry();
    private final PartySseChannel channel = new PartySseChannel(objectMapper, registry);

    @Test
    void 참여_신호는_그_방에_데이터와_함께_알린다() {
        channel.onMessage(msg(new PartySseMessage(42L, "member-joined", "{\"status\":\"ACTIVE\"}", null, null)), null);

        assertThat(registry.calls).containsExactly("notify:42:member-joined:{\"status\":\"ACTIVE\"}");
    }

    @Test
    void 퇴장_신호는_나간_사람의_연결을_먼저_닫고_남은_사람에게_알린다() {
        channel.onMessage(msg(new PartySseMessage(42L, "member-left", "{}", 7L, null)), null);

        assertThat(registry.calls).containsExactly("closeMember:42:7", "notify:42:member-left:{}");
    }

    @Test
    void closed_신호는_알린_뒤_그_방의_연결을_끊는다() {
        channel.onMessage(msg(new PartySseMessage(42L, "closed", "42", null, null)), null);

        assertThat(registry.calls).containsExactly("notify:42:closed:42", "closeAll:42");
    }

    @Test
    void 참여_신호는_연결을_끊지_않는다() {
        channel.onMessage(msg(new PartySseMessage(42L, "member-joined", "{}", null, null)), null);

        assertThat(registry.calls).noneMatch(c -> c.startsWith("close"));
    }

    @Test
    void 발행_시각을_레지스트리에_넘긴다() {
        channel.onMessage(msg(new PartySseMessage(42L, "member-joined", "{}", null, 1_000L)), null);

        assertThat(registry.publishedAts).containsExactly(1_000L);
    }

    @Test
    void 발행_시각이_없는_옛_형식_메시지도_처리하고_발행_시각은_비워_넘긴다() {
        channel.onMessage(raw("{\"partyId\":42,\"event\":\"member-joined\",\"data\":\"{}\",\"leftMemberId\":null}"), null);

        assertThat(registry.calls).containsExactly("notify:42:member-joined:{}");
        assertThat(registry.publishedAts).containsOnlyNulls().hasSize(1);
    }

    @Test
    void 발행_시각이_실린_새_형식_메시지를_옛_형식으로도_읽을_수_있다() {
        String body = objectMapper.writeValueAsString(new PartySseMessage(42L, "member-left", "{}", 7L, 1_000L));

        OldPartySseMessage old = objectMapper.readValue(body, OldPartySseMessage.class);

        assertThat(old).isEqualTo(new OldPartySseMessage(42L, "member-left", "{}", 7L));
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
    record OldPartySseMessage(Long partyId, String event, String data, Long leftMemberId) {
    }

    static class RecordingRegistry extends PartySseRegistry {
        final List<String> calls = new ArrayList<>();
        final List<Long> publishedAts = new ArrayList<>();

        RecordingRegistry() {
            super(new SimpleMeterRegistry());
        }

        @Override
        public void notify(Long partyId, String event, String data, Long publishedAt) {
            calls.add("notify:" + partyId + ":" + event + ":" + data);
            publishedAts.add(publishedAt);
        }

        @Override
        public void closeAll(Long partyId) {
            calls.add("closeAll:" + partyId);
        }

        @Override
        public void closeMember(Long partyId, Long memberId) {
            calls.add("closeMember:" + partyId + ":" + memberId);
        }
    }
}
