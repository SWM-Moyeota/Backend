package team.codingforest.moyeota.socket.auth;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 세션마다 참여 확인을 마친 방을 기억함, 같은 방의 메시지·위치·멤버 목적지를 구독할 때 API 를 한 번만 부르기 위함
 * 방을 나가면 ChatSubscriptionCleaner 가 지우고, 연결이 끝나면 세션째 지움
 */
@Component
public class VerifiedRooms {
    private final Map<String, Set<Long>> roomsBySession = new ConcurrentHashMap<>();

    /**
     * 이 세션이 그 방 확인을 이미 마쳤는지 봄
     *
     * @param sessionId  STOMP 세션 id
     * @param chatRoomId 채팅방 id
     * @return 확인을 마쳤으면 true
     */
    public boolean contains(String sessionId, Long chatRoomId) {
        Set<Long> rooms = roomsBySession.get(sessionId);
        return rooms != null && rooms.contains(chatRoomId);
    }

    /**
     * 참여 확인을 마친 방을 기억함
     *
     * @param sessionId  STOMP 세션 id
     * @param chatRoomId 채팅방 id
     */
    public void add(String sessionId, Long chatRoomId) {
        roomsBySession.computeIfAbsent(sessionId, id -> ConcurrentHashMap.newKeySet()).add(chatRoomId);
    }

    /**
     * 방을 나간 세션에서 그 방을 지움, 다시 구독하면 API 에 다시 물음
     *
     * @param sessionId  STOMP 세션 id
     * @param chatRoomId 채팅방 id
     */
    public void remove(String sessionId, Long chatRoomId) {
        Set<Long> rooms = roomsBySession.get(sessionId);

        if (rooms != null) {
            rooms.remove(chatRoomId);
        }
    }

    /**
     * 연결이 끝난 세션의 기록을 지움
     *
     * @param event 연결 종료 이벤트
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        roomsBySession.remove(event.getSessionId());
    }
}
