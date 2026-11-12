package team.codingforest.moyeota.matching.party.infrastructure;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.party.domain.MeetingNotifier;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Primary
@ConditionalOnExpression("!'${fcm.service-account-path:}'.isEmpty()")
@RequiredArgsConstructor
public class FcmMeetingNotifier implements MeetingNotifier {
    private final FirebaseMessaging firebaseMessaging;
    private final UserAccess userAccess;

    @Override
    public void notifyMeetingTime(Long partyId, List<Long> memberIds) {
        send(Push.MEETING_TIME, partyId, memberIds);
    }

    @Override
    public void remindDeparture(Long partyId, List<Long> memberIds) {
        send(Push.DEPARTURE_REMINDER, partyId, memberIds);
    }

    private void send(Push push, Long partyId, List<Long> memberIds) {
        Map<Long, String> tokens = userAccess.findFcmTokens(memberIds);

        if(tokens.isEmpty()) {
            log.warn("[{}] FCM 토큰이 등록된 받는 사람이 없음 partyId={}, 받는 사람={}명", push.label, partyId, memberIds.size());
            return;
        }

        MulticastMessage message = MulticastMessage.builder()
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .setApnsConfig(apnsConfig(push, partyId))
                .putData("type", push.type)
                .putData("partyId", String.valueOf(partyId))
                .addAllTokens(tokens.values())
                .build();

        ApiFuture<BatchResponse> future = firebaseMessaging.sendEachForMulticastAsync(message);

        ApiFutures.addCallback(future, new ApiFutureCallback<>() {
            @Override
            public void onFailure(Throwable t) {
                log.error("[{}] 전송 실패 partyId={}", push.label, partyId, t);
            }

            @Override
            public void onSuccess(BatchResponse result) {
                log.info("[{}] 전송완료 partyId={}, 성공={}, 실패={}", push.label, partyId, result.getSuccessCount(), result.getFailureCount());
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     *  iOS를 위한 설정 알림을 APNs에 전달
     */
    private ApnsConfig apnsConfig(Push push, Long partyId) {
        return ApnsConfig.builder()
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .putHeader("apns-collapse-id", String.valueOf(push.collapseIdBase + partyId % 100_000))
                .setAps(Aps.builder()
                        .setAlert(ApsAlert.builder().setTitle(push.title).setBody(push.body).build())
                        .setSound("default")
                        .setThreadId("party-" + partyId)
                        .build())
                .build();
    }

    /** collapse-id 는 채팅(200000 + chatRoomId % 100000)과 겹치지 않게 종류마다 10만 단위 구간을 쓴다 */
    private enum Push {
        MEETING_TIME("PARTY_MEETING_TIME", "약속 시간이에요", "도착하면 도착 버튼을, 모두 모이면 출발 버튼을 눌러 주세요.", 300_000, "약속 시각 알림"),
        DEPARTURE_REMINDER("PARTY_DEPARTURE_REMINDER", "출발하셨나요?", "모두 모였으면 출발 버튼을 눌러 주세요.", 400_000, "출발 재촉 알림");

        private final String type;
        private final String title;
        private final String body;
        private final long collapseIdBase;
        private final String label;

        Push(String type, String title, String body, long collapseIdBase, String label) {
            this.type = type;
            this.title = title;
            this.body = body;
            this.collapseIdBase = collapseIdBase;
            this.label = label;
        }
    }
}
