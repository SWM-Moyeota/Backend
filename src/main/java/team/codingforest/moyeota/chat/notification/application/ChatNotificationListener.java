package team.codingforest.moyeota.chat.notification.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota._config.AsyncConfig;
import team.codingforest.moyeota.chat.member.domain.ChatMember;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.member.domain.MemberProvider;
import team.codingforest.moyeota.chat.message.domain.enums.ChatMessageType;
import team.codingforest.moyeota.chat.message.dto.ChatMessageResult;
import team.codingforest.moyeota.chat.message.event.ChatMessageSentEvent;
import team.codingforest.moyeota.chat.notification.domain.ChatMessageNotification;
import team.codingforest.moyeota.chat.notification.domain.ChatNotifier;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class ChatNotificationListener {

    private static final String TASK_NAME = "chat.push";
    private static final int PREVIEW_LENGTH = 30;
    private static final String UNKNOWN_SENDER = "알 수 없음";
    private static final String LOCATION_PREVIEW = "위치를 공유했습니다";

    private final ChatRoomUsers chatRoomUsers;
    private final MemberProvider memberProvider;
    private final ChatNotifier chatNotifier;
    private final AfterCommitExecutor afterCommitExecutor;
    private final Executor chatPushExecutor;

    public ChatNotificationListener(ChatRoomUsers chatRoomUsers,
                                    MemberProvider memberProvider,
                                    ChatNotifier chatNotifier,
                                    AfterCommitExecutor afterCommitExecutor,
                                    @Qualifier(AsyncConfig.CHAT_PUSH_EXECUTOR) Executor chatPushExecutor) {
        this.chatRoomUsers = chatRoomUsers;
        this.memberProvider = memberProvider;
        this.chatNotifier = chatNotifier;
        this.afterCommitExecutor = afterCommitExecutor;
        this.chatPushExecutor = chatPushExecutor;
    }

    /**
     * 커밋 후 푸시 실행기에 넘김. 늦은 푸시는 가치가 낮아 event_publication 에 남기지 않음
     * 실행기가 거절하면 after.commit.task fail 카운터만 남기고 버림
     *
     * @param event 메시지 전송 이벤트
     */
    @EventListener
    public void onMessageSent(ChatMessageSentEvent event) {
        afterCommitExecutor.execute(TASK_NAME,
                () -> chatPushExecutor.execute(() -> notifyQuietly(event)));
    }

    private void notifyQuietly(ChatMessageSentEvent event) {
        try {
            notifyReceiver(event);
        } catch (Exception e) {
            log.error("채팅 푸시 알림 전송 실패 chatRoomId={}, messageId={}",
                    event.result().chatRoomId(), event.result().id(), e);
        }
    }

    private void notifyReceiver(ChatMessageSentEvent event) {
        ChatMessageResult result = event.result();

        List<Long> receiverIds = chatRoomUsers.findAllByChatRoomId(result.chatRoomId()).stream()
                .filter(user -> !user.hasLeft())
                .filter(user -> !user.isNotificationMuted())
                .map(ChatRoomUser::getUserId)
                .filter(userId -> !userId.equals(event.senderId()))
                .toList();

        if (receiverIds.isEmpty()) {
            log.debug("푸시 수신자 없음 chatRoomId={}", result.chatRoomId());
            return;
        }

        ChatMember sender = memberProvider.findMembers(List.of(event.senderId())).get(event.senderId());

        chatNotifier.notifyNewMessage(receiverIds, new ChatMessageNotification(
                result.chatRoomId(),
                result.id(),
                result.seq(),
                result.publicId(),
                sender == null ? UNKNOWN_SENDER : sender.nickname(),
                preview(result)
        ));
    }

    private String preview(ChatMessageResult result) {
        if (result.type() == ChatMessageType.LOCATION) {
            return LOCATION_PREVIEW;
        }
        String content = result.content();
        if (content == null) {
            return "";
        }
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "...";
    }
}
