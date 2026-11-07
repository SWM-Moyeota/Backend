package team.codingforest.moyeota.socket.session;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.chat.contract.ChatDestinations;
import team.codingforest.moyeota.socket.auth.VerifiedRooms;

/**
 * 방을 나간 사용자의 그 방 구독을 이 서버에서 끊음
 * 확인 기록도 지워서 다시 구독하면 API 에 다시 묻게 함
 */
@Component
@RequiredArgsConstructor
public class ChatSubscriptionCleaner {

    private final SimpUserRegistry userRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final VerifiedRooms verifiedRooms;

    /**
     * 사용자의 모든 세션에서 그 방과 하위 목적지 구독을 해제함
     *
     * @param userId     방을 나간 사용자 id
     * @param chatRoomId 나간 채팅방 id
     */
    public void unsubscribeRoom(Long userId, Long chatRoomId) {
        SimpUser user = userRegistry.getUser(String.valueOf(userId));

        if (user == null) {
            return;
        }

        String room = ChatDestinations.room(chatRoomId);
        for (SimpSession session : user.getSessions()) {
            verifiedRooms.remove(session.getId(), chatRoomId);

            for (SimpSubscription subscription : session.getSubscriptions()) {
                if (isRoom(subscription.getDestination(), room)) {
                    messagingTemplate.getMessageChannel().send(unsubscribe(session.getId(), subscription.getId()));
                }
            }
        }
    }

    private static boolean isRoom(String destination, String room) {
        return destination.equals(room) || destination.startsWith(room + "/");
    }

    private static Message<byte[]> unsubscribe(String sessionId, String subscriptionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
