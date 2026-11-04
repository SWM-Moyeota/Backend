package team.codingforest.moyeota.matching.party.infrastructure;

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
import team.codingforest.moyeota.user.api.UserAccess;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FcmMeetingNotifierUnitTest {
    private static final Long PARTY_ID = 123456L;
    private static final List<Long> MEMBER_IDS = List.of(1L, 2L);

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private UserAccess userAccess;

    @InjectMocks
    private FcmMeetingNotifier notifier;

    // ───────────────────────── notifyMeetingTime ─────────────────────────

    @DisplayName("notifyMeetingTime - iOS 시스템이 표시할 제목·내용·묶음 값을 APNs 로 보낸다")
    @Test
    void notifyMeetingTime_apns() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(n -> n.notifyMeetingTime(PARTY_ID, MEMBER_IDS));

        /*
         * verify
         */
        assertApns(payload, "323456", "약속 시간이에요", "도착하면 도착 버튼을, 모두 모이면 출발 버튼을 눌러 주세요.");
    }

    @DisplayName("notifyMeetingTime - Android 우선순위와 data 키(type, partyId)를 보낸다")
    @Test
    void notifyMeetingTime_androidAndData() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(n -> n.notifyMeetingTime(PARTY_ID, MEMBER_IDS));

        /*
         * verify
         */
        assertAndroidAndData(payload, "PARTY_MEETING_TIME");
    }

    // ───────────────────────── remindDeparture ─────────────────────────

    @DisplayName("remindDeparture - iOS 시스템이 표시할 제목·내용·묶음 값을 APNs 로 보낸다")
    @Test
    void remindDeparture_apns() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(n -> n.remindDeparture(PARTY_ID, MEMBER_IDS));

        /*
         * verify
         */
        assertApns(payload, "423456", "출발하셨나요?", "출발했다면 출발 버튼을 눌러 주세요.");
    }

    @DisplayName("remindDeparture - Android 우선순위와 data 키(type, partyId)를 보낸다")
    @Test
    void remindDeparture_androidAndData() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(n -> n.remindDeparture(PARTY_ID, MEMBER_IDS));

        /*
         * verify
         */
        assertAndroidAndData(payload, "PARTY_DEPARTURE_REMINDER");
    }

    // ───────────────────────── notifyNoShow ─────────────────────────

    @DisplayName("notifyNoShow - iOS 시스템이 표시할 제목·내용·묶음 값을 APNs 로 보낸다")
    @Test
    void notifyNoShow_apns() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(n -> n.notifyNoShow(PARTY_ID, MEMBER_IDS));

        /*
         * verify
         */
        assertApns(payload, "523456", "노쇼로 기록됐어요", "실제로 함께 탔다면 이의 신청을 해 주세요.");
    }

    @DisplayName("notifyNoShow - Android 우선순위와 data 키(type, partyId)를 보낸다")
    @Test
    void notifyNoShow_androidAndData() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(n -> n.notifyNoShow(PARTY_ID, MEMBER_IDS));

        /*
         * verify
         */
        assertAndroidAndData(payload, "PARTY_NO_SHOW");
    }

    // ───────────────────────── 공통 ─────────────────────────

    @DisplayName("notifyMeetingTime - collapse-id 는 partyId 를 10만으로 나눈 나머지를 쓴다")
    @Test
    void notifyMeetingTime_collapseIdWraps() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(n -> n.notifyMeetingTime(7L, MEMBER_IDS));

        /*
         * verify
         */
        assertThat(payload.<String>read("$.apns.headers.apns-collapse-id")).isEqualTo("300007");
        assertThat(payload.<String>read("$.apns.payload.aps.thread-id")).isEqualTo("party-7");
    }

    @DisplayName("notifyMeetingTime - FCM 토큰이 등록된 받는 사람이 없으면 보내지 않는다")
    @Test
    void notifyMeetingTime_noTokens() {

        /*
         * settings
         */
        given(userAccess.findFcmTokens(MEMBER_IDS)).willReturn(Map.of());

        /*
         * test
         */
        notifier.notifyMeetingTime(PARTY_ID, MEMBER_IDS);

        /*
         * verify
         */
        verifyNoInteractions(firebaseMessaging);
    }

    private static void assertApns(DocumentContext payload, String collapseId, String title, String body) {
        assertThat(payload.<String>read("$.apns.headers.apns-push-type")).isEqualTo("alert");
        assertThat(payload.<String>read("$.apns.headers.apns-priority")).isEqualTo("10");
        assertThat(payload.<String>read("$.apns.headers.apns-collapse-id")).isEqualTo(collapseId);
        assertThat(payload.<String>read("$.apns.payload.aps.alert.title")).isEqualTo(title);
        assertThat(payload.<String>read("$.apns.payload.aps.alert.body")).isEqualTo(body);
        assertThat(payload.<String>read("$.apns.payload.aps.sound")).isEqualTo("default");
        assertThat(payload.<String>read("$.apns.payload.aps.thread-id")).isEqualTo("party-123456");
    }

    private static void assertAndroidAndData(DocumentContext payload, String type) {
        assertThat(payload.<String>read("$.android.priority")).isEqualTo("high");
        assertThat(payload.<Map<String, String>>read("$.data")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "type", type,
                "partyId", String.valueOf(PARTY_ID)));
    }

    /** 실제 전송되는 FCM JSON 으로 검증한다 - MulticastMessage 는 값을 꺼내는 공개 메서드가 없다 */
    private DocumentContext send(Consumer<FcmMeetingNotifier> call) throws IOException {
        given(userAccess.findFcmTokens(MEMBER_IDS)).willReturn(Map.of(1L, "token-1", 2L, "token-2"));
        given(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
                .willReturn(ApiFutures.immediateFuture(mock(BatchResponse.class)));

        call.accept(notifier);

        ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(firebaseMessaging).sendEachForMulticastAsync(captor.capture());
        List<?> messages = ReflectionTestUtils.invokeMethod(captor.getValue(), "getMessageList");
        assertThat(messages).hasSize(2);
        return JsonPath.parse(GsonFactory.getDefaultInstance().toString(messages.get(0)));
    }
}
