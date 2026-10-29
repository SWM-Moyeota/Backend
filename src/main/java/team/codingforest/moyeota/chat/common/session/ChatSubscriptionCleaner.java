package team.codingforest.moyeota.chat.common.session;

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

@Component
@RequiredArgsConstructor
public class ChatSubscriptionCleaner {

    private final SimpUserRegistry userRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    public void unsubscribeRoom(Long userId, Long chatRoomId) {
        SimpUser user = userRegistry.getUser(String.valueOf(userId));

        if (user == null) {
            return;
        }

        String room = ChatDestinations.room(chatRoomId);
        for (SimpSession session : user.getSessions()) {
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
