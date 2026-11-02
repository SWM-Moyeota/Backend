package team.codingforest.moyeota.chat.notification.infrastructure;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.core.ApiFutures;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import team.codingforest.moyeota.chat.notification.domain.ChatMessageNotification;
import team.codingforest.moyeota.user.api.UserAccess;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FcmChatNotifierUnitTest {

    private static final UUID SENDER_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private UserAccess userAccess;

    @InjectMocks
    private FcmChatNotifier notifier;

    @DisplayName("notifyNewMessage - iOS 시스템이 표시할 제목·내용·묶음 값을 APNs 로 보낸다")
    @Test
    void notifyNewMessage_apns() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(notification(123456L, "민수", "강남역에서 만나요"));

        /*
         * verify
         */
        assertThat(payload.<String>read("$.apns.headers.apns-push-type")).isEqualTo("alert");
        assertThat(payload.<String>read("$.apns.headers.apns-priority")).isEqualTo("10");
        assertThat(payload.<String>read("$.apns.headers.apns-collapse-id")).isEqualTo("223456");
        assertThat(payload.<String>read("$.apns.payload.aps.alert.title")).isEqualTo("민수");
        assertThat(payload.<String>read("$.apns.payload.aps.alert.body")).isEqualTo("강남역에서 만나요");
        assertThat(payload.<String>read("$.apns.payload.aps.sound")).isEqualTo("default");
        assertThat(payload.<String>read("$.apns.payload.aps.thread-id")).isEqualTo("chat-123456");
    }

    @DisplayName("notifyNewMessage - 닉네임과 미리보기가 공백뿐이면 기본 문구를 표시한다")
    @Test
    void notifyNewMessage_blankText() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(notification(1L, "  ", " "));

        /*
         * verify
         */
        assertThat(payload.<String>read("$.apns.payload.aps.alert.title")).isEqualTo("동승자");
        assertThat(payload.<String>read("$.apns.payload.aps.alert.body")).isEqualTo("새 메시지가 도착했어요");
    }

    @DisplayName("notifyNewMessage - APNs 를 더해도 Android 설정과 data 키는 그대로 보낸다")
    @Test
    void notifyNewMessage_keepAndroid() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(notification(7L, "민수", "안녕하세요"));

        /*
         * verify
         */
        assertThat(payload.<String>read("$.android.priority")).isEqualTo("high");
        assertThat(payload.<Map<String, String>>read("$.data")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "type", "CHAT_MESSAGE",
                "chatRoomId", "7",
                "messageId", "20",
                "seq", "30",
                "senderPublicId", SENDER_PUBLIC_ID.toString(),
                "senderNickname", "민수",
                "preview", "안녕하세요"));
    }

    private ChatMessageNotification notification(Long chatRoomId, String nickname, String preview) {
        return new ChatMessageNotification(chatRoomId, 20L, 30L, SENDER_PUBLIC_ID, nickname, preview);
    }

    /** 실제 전송되는 FCM JSON 으로 검증한다 - MulticastMessage 는 값을 꺼내는 공개 메서드가 없다 */
    private DocumentContext send(ChatMessageNotification notification) throws IOException {
        given(userAccess.findFcmTokens(List.of(1L))).willReturn(Map.of(1L, "token-1"));
        given(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
                .willReturn(ApiFutures.immediateFuture(mock(BatchResponse.class)));

        notifier.notifyNewMessage(List.of(1L), notification);

        ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(firebaseMessaging).sendEachForMulticastAsync(captor.capture());
        List<?> messages = ReflectionTestUtils.invokeMethod(captor.getValue(), "getMessageList");
        return JsonPath.parse(GsonFactory.getDefaultInstance().toString(messages.get(0)));
    }
}
