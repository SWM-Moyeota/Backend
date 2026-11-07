package team.codingforest.moyeota.faresplit.infrastructure;

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
import java.util.HashMap;
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
class FcmFareSplitNotifierUnitTest {
    private static final Long PARTY_ID = 123456L;
    private static final Long PAYER_ID = 1L;
    private static final Long SENDER_ID = 2L;
    private static final Long OTHER_SENDER_ID = 3L;
    private static final List<Long> SENDER_IDS = List.of(SENDER_ID, OTHER_SENDER_ID);

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private UserAccess userAccess;

    @InjectMocks
    private FcmFareSplitNotifier notifier;

    // ───────────────────────── requestTransfer ─────────────────────────

    @DisplayName("requestTransfer - iOS 에 금액(천 단위 쉼표)을 넣은 제목·내용과 묶음 값을 보낸다")
    @Test
    void requestTransfer_apns() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(SENDER_IDS, n -> n.requestTransfer(PARTY_ID, SENDER_IDS, 6_240));

        /*
         * verify
         */
        assertApns(payload, "723456", "택시비를 나눠 내요", "6,240원을 보내 주세요. 받을 계좌는 앱에서 볼 수 있어요.");
    }

    @DisplayName("requestTransfer - Android 우선순위와 data 키(type, partyId, amount)를 보낸다")
    @Test
    void requestTransfer_androidAndData() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(SENDER_IDS, n -> n.requestTransfer(PARTY_ID, SENDER_IDS, 6_240));

        /*
         * verify
         */
        assertAndroidAndData(payload, Map.of("type", "FARE_SPLIT_REQUEST", "partyId", "123456", "amount", "6240"));
    }

    // ───────────────────────── remindTransfer ─────────────────────────

    @DisplayName("remindTransfer - 송금 요청과 같은 collapse-id 로 보내 알림 센터에 마지막 요청만 남긴다")
    @Test
    void remindTransfer_apns() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(SENDER_IDS, n -> n.remindTransfer(PARTY_ID, SENDER_IDS, 12_000));

        /*
         * verify
         */
        assertApns(payload, "723456", "택시비를 아직 보내지 않았어요", "12,000원을 보낸 뒤 보냈어요를 눌러 주세요.");
    }

    @DisplayName("remindTransfer - Android 우선순위와 data 키(type, partyId, amount)를 보낸다")
    @Test
    void remindTransfer_androidAndData() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(SENDER_IDS, n -> n.remindTransfer(PARTY_ID, SENDER_IDS, 12_000));

        /*
         * verify
         */
        assertAndroidAndData(payload, Map.of("type", "FARE_SPLIT_REMINDER", "partyId", "123456", "amount", "12000"));
    }

    // ───────────────────────── requestConfirm ─────────────────────────

    @DisplayName("requestConfirm - 받는 사람에게 입금 확인 요청을 보낸다(금액 없음)")
    @Test
    void requestConfirm() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(List.of(PAYER_ID), n -> n.requestConfirm(PARTY_ID, PAYER_ID));

        /*
         * verify
         */
        assertApns(payload, "823456", "택시비가 들어왔는지 확인해 주세요",
                "보냈다는 송금이 하루째 확인을 기다리고 있어요. 입금을 확인했다면 받았어요를 눌러 주세요.");
        assertAndroidAndData(payload, Map.of("type", "FARE_SPLIT_CONFIRM_REQUEST", "partyId", "123456"));
    }

    // ───────────────────────── notifyNotReceived ─────────────────────────

    @DisplayName("notifyNotReceived - 그 송금의 보내는 사람에게 못 받음 알림을 보낸다")
    @Test
    void notifyNotReceived() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(List.of(SENDER_ID), n -> n.notifyNotReceived(PARTY_ID, SENDER_ID));

        /*
         * verify
         */
        assertApns(payload, "923456", "택시비가 아직 들어오지 않았대요", "받을 계좌와 금액을 확인하고 다시 보내 주세요.");
        assertAndroidAndData(payload, Map.of("type", "FARE_SPLIT_NOT_RECEIVED", "partyId", "123456"));
    }

    // ───────────────────────── notifyCompleted ─────────────────────────

    @DisplayName("notifyCompleted - 받는 사람과 보내는 사람 전원에게 완료 알림을 보낸다")
    @Test
    void notifyCompleted() throws IOException {

        /*
         * test
         */
        List<Long> participants = List.of(PAYER_ID, SENDER_ID, OTHER_SENDER_ID);
        DocumentContext payload = send(participants, n -> n.notifyCompleted(PARTY_ID, participants));

        /*
         * verify
         */
        assertApns(payload, "1023456", "택시비 정산이 끝났어요", "모든 송금이 확인됐어요.");
        assertAndroidAndData(payload, Map.of("type", "FARE_SPLIT_COMPLETED", "partyId", "123456"));
    }

    // ───────────────────────── 공통 ─────────────────────────

    @DisplayName("requestTransfer - collapse-id 는 partyId 를 10만으로 나눈 나머지를 쓴다")
    @Test
    void requestTransfer_collapseIdWraps() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(SENDER_IDS, n -> n.requestTransfer(7L, SENDER_IDS, 6_240));

        /*
         * verify
         */
        assertThat(payload.<String>read("$.apns.headers.apns-collapse-id")).isEqualTo("700007");
        assertThat(payload.<String>read("$.apns.payload.aps.thread-id")).isEqualTo("party-7");
    }

    @DisplayName("notifyCompleted - FCM 토큰이 등록된 받는 사람이 없으면(탈퇴 등) 보내지 않는다")
    @Test
    void notifyCompleted_noTokens() {

        /*
         * settings
         */
        given(userAccess.findFcmTokens(List.of(PAYER_ID))).willReturn(Map.of());

        /*
         * test
         */
        notifier.notifyCompleted(PARTY_ID, List.of(PAYER_ID));

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

    private static void assertAndroidAndData(DocumentContext payload, Map<String, String> data) {
        assertThat(payload.<String>read("$.android.priority")).isEqualTo("high");
        assertThat(payload.<Map<String, String>>read("$.data")).containsExactlyInAnyOrderEntriesOf(data);
    }

    /** 실제 전송되는 FCM JSON 으로 검증한다 - MulticastMessage 는 값을 꺼내는 공개 메서드가 없다. 받는 사람 수만큼 메시지가 나간다 */
    private DocumentContext send(List<Long> recipientIds, Consumer<FcmFareSplitNotifier> call) throws IOException {
        Map<Long, String> tokens = new HashMap<>();
        recipientIds.forEach(id -> tokens.put(id, "token-" + id));
        given(userAccess.findFcmTokens(recipientIds)).willReturn(tokens);
        given(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
                .willReturn(ApiFutures.immediateFuture(mock(BatchResponse.class)));

        call.accept(notifier);

        ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(firebaseMessaging).sendEachForMulticastAsync(captor.capture());
        List<?> messages = ReflectionTestUtils.invokeMethod(captor.getValue(), "getMessageList");
        assertThat(messages).hasSize(recipientIds.size());
        return JsonPath.parse(GsonFactory.getDefaultInstance().toString(messages.get(0)));
    }
}
