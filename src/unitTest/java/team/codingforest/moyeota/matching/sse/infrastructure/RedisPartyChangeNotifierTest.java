package team.codingforest.moyeota.matching.sse.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import team.codingforest.moyeota.matching.party.dto.MemberJoined;
import team.codingforest.moyeota.matching.party.dto.MemberLeft;
import team.codingforest.moyeota.matching.party.dto.PartyClosed;
import team.codingforest.moyeota.matching.party.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.sse.dto.PartySseMessage;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  방 변화 신호 → Redis 채널 발행. 로컬 레지스트리를 직접 부르지 않고 채널로만 낸다 -
 *  인스턴스가 몇 대든 채널을 구독하는 쪽(PartySseChannel)이 자기 메모리의 연결에 전달한다.
 *  언제 내는지(참여·퇴장·닫힘, 커밋 후)는 PartyServiceTest 가 본다.
 */
class RedisPartyChangeNotifierTest {
    private static final Long 방 = 42L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingRedis redis = new RecordingRedis();
    private final RedisPartyChangeNotifier notifier = new RedisPartyChangeNotifier(redis, objectMapper);

    @Test
    void 참여는_들어온_사람_정보를_데이터로_실어_채널에_낸다() {
        UUID publicId = UUID.randomUUID();
        MemberJoined event = MemberJoined.from(new PartyDetailResult.MemberInfo(publicId, "철수", null, null, 3, false), "ACTIVE", 2L);

        notifier.memberJoined(방, event);

        PartySseMessage sent = onlySent();
        assertThat(sent.partyId()).isEqualTo(방);
        assertThat(sent.event()).isEqualTo("member-joined");
        assertThat(sent.leftMemberId()).as("참여는 닫을 연결이 없다").isNull();
        assertThat(objectMapper.readValue(sent.data(), MemberJoined.class)).isEqualTo(event);
    }

    @Test
    void 퇴장은_나간_사람의_내부_번호와_publicId_를_함께_실어_채널에_낸다() {
        // 내부 번호는 연결을 닫는 데만 쓰고, 앱에는 publicId 만 간다
        UUID publicId = UUID.randomUUID();
        UUID hostPublicId = UUID.randomUUID();
        MemberLeft event = MemberLeft.from(publicId, "ACTIVE", hostPublicId, 3L);

        notifier.memberLeft(방, 7L, event);

        PartySseMessage sent = onlySent();
        assertThat(sent.event()).isEqualTo("member-left");
        assertThat(sent.leftMemberId()).isEqualTo(7L);
        assertThat(sent.data()).contains(publicId.toString());
        assertThat(objectMapper.readValue(sent.data(), MemberLeft.class)).isEqualTo(event);
    }

    @Test
    void closed_는_상태만_실어_채널에_낸다() {
        notifier.closed(방, PartyClosed.from("FINISHED"));

        PartySseMessage sent = onlySent();
        assertThat(sent.partyId()).isEqualTo(방);
        assertThat(sent.event()).isEqualTo("closed");
        assertThat(sent.data()).isEqualTo("{\"status\":\"FINISHED\"}");
        assertThat(sent.leftMemberId()).isNull();
    }

    @Test
    void 모든_신호는_같은_채널로_낸다() {
        notifier.closed(방, PartyClosed.from("FINISHED"));

        assertThat(redis.channels).containsExactly(PartySseChannel.TOPIC);
    }

    private PartySseMessage onlySent() {
        assertThat(redis.sent).hasSize(1);
        return objectMapper.readValue(redis.sent.getFirst(), PartySseMessage.class);
    }

    /** Redis 없이 convertAndSend 호출만 기록한다 - 책임은 "어느 채널에 무엇을 내느냐" 뿐이다 */
    static class RecordingRedis extends StringRedisTemplate {
        final List<String> channels = new ArrayList<>();
        final List<String> sent = new ArrayList<>();

        @Override
        public Long convertAndSend(String channel, Object message) {
            channels.add(channel);
            sent.add((String) message);
            return 1L;
        }
    }
}
