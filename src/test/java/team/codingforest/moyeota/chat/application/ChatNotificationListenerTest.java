package team.codingforest.moyeota.chat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.chat.application.dto.ChatMessageResult;
import team.codingforest.moyeota.chat.application.event.ChatMessageSentEvent;
import team.codingforest.moyeota.chat.domain.ChatMember;
import team.codingforest.moyeota.chat.domain.ChatMessageNotification;
import team.codingforest.moyeota.chat.domain.ChatMessageType;
import team.codingforest.moyeota.chat.domain.ChatNotifier;
import team.codingforest.moyeota.chat.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.domain.MemberProvider;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatNotificationListenerTest {
    private static final Long ROOM_ID = 1L;
    private static final Long SENDER_ID = 7L;
    private static final Long RECEIVER_ID = 8L;
    private static final Long LEFT_ID = 9L;
    private static final Long MUTED_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");

    @Mock
    private ChatRoomUsers chatRoomUsers;
    @Mock
    private MemberProvider memberProvider;
    @Mock
    private ChatNotifier chatNotifier;
    @InjectMocks
    private ChatNotificationListener listener;


    private ChatMessageSentEvent event(ChatMessageResult result) {
        return new ChatMessageSentEvent(SENDER_ID, result);
    }

    private ChatMessageResult textResult(String content) {
        return new ChatMessageResult(100L, ROOM_ID, UUID.randomUUID(), content, ChatMessageType.TEXT, NOW, false);
    }

    private ChatRoomUser active(Long userId) {
        return ChatRoomUser.restore(userId, ROOM_ID, null, false, NOW, NOW, null);
    }

    private ChatRoomUser left(Long userId) {
        return ChatRoomUser.restore(userId, ROOM_ID, null, false, NOW, NOW, NOW);
    }

    private ChatRoomUser muted(Long userId) {
        return ChatRoomUser.restore(userId, ROOM_ID, null, true, NOW, NOW, null);
    }

    @Test
    void 발신자_나간사람_음소거한사람은_수신자_제외() {
        given(chatRoomUsers.findAllByChatRoomId(ROOM_ID)).willReturn(List.of(
                active(SENDER_ID), active(RECEIVER_ID), left(LEFT_ID), muted(MUTED_ID)));
        given(memberProvider.findMembers(anyList()))
                .willReturn(Map.of(SENDER_ID, new ChatMember(SENDER_ID, UUID.randomUUID(), "영희", null)));

        listener.onMessageSent(event(textResult("안녕")));

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.captor();
        verify(chatNotifier).notifyNewMessage(captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(RECEIVER_ID);
    }

    @Test
    void 수신자가_없으면_발송하지_않는다() {
        given(chatRoomUsers.findAllByChatRoomId(ROOM_ID)).willReturn(List.of(active(SENDER_ID)));

        listener.onMessageSent(event(textResult("안녕")));

        verify(chatNotifier, never()).notifyNewMessage(anyList(), any());
    }

    @Test
    void 위치_메시지는_내용_대신_고정_문구를_보낸다() {
        given(chatRoomUsers.findAllByChatRoomId(ROOM_ID)).willReturn(List.of(active(SENDER_ID), active(RECEIVER_ID)));
        given(memberProvider.findMembers(anyList()))
                .willReturn(Map.of(SENDER_ID, new ChatMember(SENDER_ID, UUID.randomUUID(), "영희", null)));

        listener.onMessageSent(event(new ChatMessageResult(
                100L, ROOM_ID, UUID.randomUUID(), "37.5665,126.9780", ChatMessageType.LOCATION, NOW, false)));

        ArgumentCaptor<ChatMessageNotification> captor = ArgumentCaptor.captor();
        verify(chatNotifier).notifyNewMessage(anyList(), captor.capture());
        assertThat(captor.getValue().preview()).isEqualTo("위치를 공유했습니다");
    }

    @Test
    void 발송_중_예외가_나도_밖으로_전파되지_않는다() {
        given(chatRoomUsers.findAllByChatRoomId(ROOM_ID)).willThrow(new RuntimeException("DB 장애"));

        listener.onMessageSent(event(textResult("안녕")));
    }

}