package team.codingforest.moyeota.matching.sse.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartySseChannel implements MessageListener {
    public static final String TOPIC = "party:events";
    static final String LEFT_PREFIX = "left:";

    private final PartySseRegistry partySseRegistry;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        try {
            String body = new String(message.getBody());
            int sep = body.indexOf(":");

            if(sep < 0) {
                log.warn("잘못된 SSE 신호 무시 body={}", body);
                return;
            }

            Long partyId = Long.valueOf(body.substring(0, sep));
            String event = body.substring(sep+1);

            // "left:<memberId>" 는 앱에 알리는 신호가 아니다 - 나간 사람의 연결을 이 인스턴스가 들고 있으면 닫는다
            if(event.startsWith(LEFT_PREFIX)) {
                partySseRegistry.closeMember(partyId, Long.valueOf(event.substring(LEFT_PREFIX.length())));
                return;
            }

            partySseRegistry.notify(partyId, event);
            if("closed".equals(event)) partySseRegistry.closeAll(partyId);
        } catch (Exception e) {
            log.warn("SSE 처리 실패", e);
        }
    }
}
