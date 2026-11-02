package team.codingforest.moyeota.dispatch.ride.infrastructure;

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
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.user.api.UserAccess;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FcmPassengerNotifierUnitTest {

    private static final Long PARTY_ID = 10L;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private PartyAccess partyAccess;

    @Mock
    private UserAccess userAccess;

    @InjectMocks
    private FcmPassengerNotifier notifier;

    @DisplayName("notifyDriverArrived - iOS 시스템이 표시할 제목·내용·묶음 값을 APNs 로 보낸다")
    @Test
    void notifyDriverArrived_apns() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send();

        /*
         * verify
         */
        assertThat(payload.<String>read("$.apns.headers.apns-push-type")).isEqualTo("alert");
        assertThat(payload.<String>read("$.apns.headers.apns-priority")).isEqualTo("10");
        assertThat(payload.<String>read("$.apns.headers.apns-collapse-id")).isEqualTo("1567");
        assertThat(payload.<String>read("$.apns.payload.aps.alert.title")).isEqualTo("기사님이 도착했어요");
        assertThat(payload.<String>read("$.apns.payload.aps.alert.body")).isEqualTo("탑승 장소에서 기사님이 기다리고 계세요.");
        assertThat(payload.<String>read("$.apns.payload.aps.sound")).isEqualTo("default");
        assertThat(payload.<String>read("$.apns.payload.aps.thread-id")).isEqualTo("party-10");
    }

    @DisplayName("notifyDriverArrived - APNs 를 더해도 Android 설정과 data 키는 그대로 보낸다")
    @Test
    void notifyDriverArrived_keepAndroid() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send();

        /*
         * verify
         */
        assertThat(payload.<String>read("$.android.priority")).isEqualTo("high");
        assertThat(payload.<Map<String, String>>read("$.data")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "type", "DRIVER_ARRIVED",
                "partyId", "10"));
    }

    /** 실제 전송되는 FCM JSON 으로 검증한다 - MulticastMessage 는 값을 꺼내는 공개 메서드가 없다 */
    private DocumentContext send() throws IOException {
        given(partyAccess.findMemberIds(PARTY_ID)).willReturn(List.of(1L));
        given(userAccess.findFcmTokens(List.of(1L))).willReturn(Map.of(1L, "token-1"));
        given(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
                .willReturn(ApiFutures.immediateFuture(mock(BatchResponse.class)));

        notifier.notifyDriverArrived(PARTY_ID);

        ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(firebaseMessaging).sendEachForMulticastAsync(captor.capture());
        List<?> messages = ReflectionTestUtils.invokeMethod(captor.getValue(), "getMessageList");
        return JsonPath.parse(GsonFactory.getDefaultInstance().toString(messages.get(0)));
    }
}
