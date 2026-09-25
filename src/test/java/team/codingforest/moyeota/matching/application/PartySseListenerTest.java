package team.codingforest.moyeota.matching.application;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import team.codingforest.moyeota.matching.api.PartyClosedEvent;
import team.codingforest.moyeota.matching.api.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.api.PartyMemberLeftEvent;
import team.codingforest.moyeota.matching.infrastructure.PartySseChannel;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  도메인 이벤트 → Redis 채널 발행. 로컬 레지스트리를 직접 부르지 않고 채널로만 낸다 -
 *  인스턴스가 몇 대든 채널을 구독하는 쪽(PartySseChannel)이 자기 메모리의 연결에 전달한다.
 */
class PartySseListenerTest {
    private static final Long 방 = 42L;
    private static final Long 멤버 = 7L;

    private final RecordingRedis redis = new RecordingRedis();
    private final PartySseListener listener = new PartySseListener(redis);

    @Test
    void 참여_이벤트는_changed_신호를_채널에_낸다() {
        listener.on(new PartyMemberJoinedEvent(방, 멤버));

        assertThat(redis.sent).containsExactly(PartySseChannel.TOPIC + "|42:changed");
    }

    @Test
    void 퇴장_이벤트도_changed_신호를_낸다() {
        listener.on(new PartyMemberLeftEvent(방, 멤버));

        assertThat(redis.sent).containsExactly(PartySseChannel.TOPIC + "|42:changed");
    }

    @Test
    void 닫힘_이벤트는_closed_신호를_낸다() {
        listener.on(new PartyClosedEvent(방, "FINISHED"));

        assertThat(redis.sent).containsExactly(PartySseChannel.TOPIC + "|42:closed");
    }

    @Test
    void 신호에는_partyId_와_이름만_싣고_상태는_싣지_않는다() {
        listener.on(new PartyClosedEvent(방, "CANCELED"));

        // FINISHED 든 CANCELED 든 앱은 closed 만 보고 상세를 재조회한다
        assertThat(redis.sent.get(0)).endsWith("|42:closed").doesNotContain("CANCELED");
    }

    /** Redis 없이 convertAndSend 호출만 기록한다 - 리스너의 책임은 "어느 채널에 무엇을 내느냐" 뿐이다 */
    static class RecordingRedis extends StringRedisTemplate {
        final List<String> sent = new ArrayList<>();

        @Override
        public Long convertAndSend(String channel, Object message) {
            sent.add(channel + "|" + message);
            return 1L;
        }
    }
}
