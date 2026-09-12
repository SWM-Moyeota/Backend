package team.codingforest.moyeota.chat.domain;

import java.util.List;

public interface ChatNotifier {
    void notifyNewMessage(List<Long> receiverIds, ChatMessageNotification notification);
}
