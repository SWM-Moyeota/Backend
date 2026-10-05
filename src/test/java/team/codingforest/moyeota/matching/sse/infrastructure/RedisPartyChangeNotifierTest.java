package team.codingforest.moyeota.matching.sse.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  방 변화 신호 → Redis 채널 발행. 로컬 레지스트리를 직접 부르지 않고 채널로만 낸다 -
 *  인스턴스가 몇 대든 채널을 구독하는 쪽(PartySseChannel)이 자기 메모리의 연결에 전달한다.
 *  언제 내는지(참여·퇴장·닫힘, 커밋 후)는 PartyServiceTest 가 본다.
 */
class RedisPartyChangeNotifierTest {
    private static final Long 방 = 42L;

    private final RecordingRedis redis = new RecordingRedis();
    private final RedisPartyChangeNotifier notifier = new RedisPartyChangeNotifier(redis);

    @Test
    void changed_는_changed_신호를_채널에_낸다() {
        notifier.changed(방);

        assertThat(redis.sent).containsExactly(PartySseChannel.TOPIC + "|42:changed");
    }

    @Test
    void closed_는_closed_신호를_채널에_낸다() {
        notifier.closed(방);

        assertThat(redis.sent).containsExactly(PartySseChannel.TOPIC + "|42:closed");
    }

    @Test
    void 신호에는_partyId_와_이름만_싣는다() {
        notifier.closed(방);

        // FINISHED 든 CANCELED 든 앱은 closed 만 보고 상세를 재조회한다 - 상태를 실으면 앱이 그걸 믿고 조회를 건너뛸 수 있다
        assertThat(redis.sent.get(0)).isEqualTo(PartySseChannel.TOPIC + "|42:closed");
    }

    /** Redis 없이 convertAndSend 호출만 기록한다 - 책임은 "어느 채널에 무엇을 내느냐" 뿐이다 */
    static class RecordingRedis extends StringRedisTemplate {
        final List<String> sent = new ArrayList<>();

        @Override
        public Long convertAndSend(String channel, Object message) {
            sent.add(channel + "|" + message);
            return 1L;
        }
    }
}
