package team.codingforest.moyeota.matching.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  Redis 채널 수신 → 로컬 레지스트리. 메시지는 "partyId:event" 한 줄이다.
 *  closed 는 알린 뒤 이 인스턴스의 연결을 끊는다 - 순서가 바뀌면 아무도 못 받는다.
 */
class PartySseChannelTest {
    private final RecordingRegistry registry = new RecordingRegistry();
    private final PartySseChannel channel = new PartySseChannel(registry);

    @Test
    void changed_신호는_그_방에_알린다() {
        channel.onMessage(msg("42:changed"), null);

        assertThat(registry.calls).containsExactly("notify:42:changed");
    }

    @Test
    void closed_신호는_알린_뒤_그_방의_연결을_끊는다() {
        channel.onMessage(msg("42:closed"), null);

        assertThat(registry.calls).containsExactly("notify:42:closed", "closeAll:42");
    }

    @Test
    void changed_는_연결을_끊지_않는다() {
        channel.onMessage(msg("42:changed"), null);

        assertThat(registry.calls).noneMatch(c -> c.startsWith("closeAll"));
    }

    @Test
    void 콜론이_없는_메시지는_무시한다() {
        channel.onMessage(msg("garbage"), null);

        assertThat(registry.calls).isEmpty();
    }

    @Test
    void 숫자가_아닌_partyId_는_구독_스레드를_죽이지_않고_무시한다() {
        // 예외가 새면 RedisMessageListenerContainer 의 리스너 스레드가 멈춰 그 뒤 신호를 영영 못 받는다
        channel.onMessage(msg("abc:changed"), null);

        assertThat(registry.calls).isEmpty();
    }

    @Test
    void 첫_콜론_기준으로_나눈다() {
        channel.onMessage(msg("42:a:b"), null);

        assertThat(registry.calls).containsExactly("notify:42:a:b");
    }

    private static Message msg(String body) {
        return new DefaultMessage(PartySseChannel.TOPIC.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }

    /** 실제 emitter 대신 호출 순서만 기록한다 */
    static class RecordingRegistry extends PartySseRegistry {
        final List<String> calls = new ArrayList<>();

        @Override
        public void notify(Long partyId, String event) {
            calls.add("notify:" + partyId + ":" + event);
        }

        @Override
        public void closeAll(Long partyId) {
            calls.add("closeAll:" + partyId);
        }
    }
}
