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
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FcmReservationReminderNotifierUnitTest {
    private static final Long PARTY_ID = 10L;
    // 한국 시각 2026-11-03 15:30
    private static final Instant DEPARTURE_AT = Instant.parse("2026-11-03T06:30:00Z");

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private UserAccess userAccess;

    @InjectMocks
    private FcmReservationReminderNotifier notifier;

    @DisplayName("notifyDepartureSoon - iOS 시스템이 표시할 제목·내용(한국 시각 출발 시각)·묶음 값을 APNs 로 보낸다")
    @Test
    void notifyDepartureSoon_apns() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(DEPARTURE_AT);

        /*
         * verify
         */
        assertThat(payload.<String>read("$.apns.headers.apns-push-type")).isEqualTo("alert");
        assertThat(payload.<String>read("$.apns.headers.apns-priority")).isEqualTo("10");
        assertThat(payload.<String>read("$.apns.headers.apns-collapse-id")).isEqualTo("600010");
        assertThat(payload.<String>read("$.apns.payload.aps.alert.title")).isEqualTo("곧 출발해요");
        assertThat(payload.<String>read("$.apns.payload.aps.alert.body")).isEqualTo("15:30 출발이에요. 늦지 않게 출발지로 와 주세요.");
        assertThat(payload.<String>read("$.apns.payload.aps.sound")).isEqualTo("default");
        assertThat(payload.<String>read("$.apns.payload.aps.thread-id")).isEqualTo("party-10");
    }

    @DisplayName("notifyDepartureSoon - 한국 시각으로 날짜가 바뀌는 출발도 한국 시각으로 보여 준다")
    @Test
    void notifyDepartureSoon_seoulTime() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(Instant.parse("2026-11-03T15:05:00Z"));   // 한국 시각 11-04 00:05

        /*
         * verify
         */
        assertThat(payload.<String>read("$.apns.payload.aps.alert.body")).isEqualTo("00:05 출발이에요. 늦지 않게 출발지로 와 주세요.");
    }

    @DisplayName("notifyDepartureSoon - APNs 를 더해도 Android 설정과 data 키는 그대로 보낸다")
    @Test
    void notifyDepartureSoon_keepAndroid() throws IOException {

        /*
         * test
         */
        DocumentContext payload = send(DEPARTURE_AT);

        /*
         * verify
         */
        assertThat(payload.<String>read("$.android.priority")).isEqualTo("high");
        assertThat(payload.<Map<String, String>>read("$.data")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "type", "RESERVATION_DEPARTURE_SOON",
                "partyId", "10",
                "departureAt", "2026-11-03T06:30:00Z"));
    }

    /** 실제 전송되는 FCM JSON 으로 검증한다 - MulticastMessage 는 값을 꺼내는 공개 메서드가 없다 */
    private DocumentContext send(Instant departureAt) throws IOException {
        given(userAccess.findFcmTokens(List.of(1L))).willReturn(Map.of(1L, "token-1"));
        given(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
                .willReturn(ApiFutures.immediateFuture(mock(BatchResponse.class)));

        notifier.notifyDepartureSoon(PARTY_ID, List.of(1L), departureAt);

        ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(firebaseMessaging).sendEachForMulticastAsync(captor.capture());
        List<?> messages = ReflectionTestUtils.invokeMethod(captor.getValue(), "getMessageList");
        return JsonPath.parse(GsonFactory.getDefaultInstance().toString(messages.get(0)));
    }
}
