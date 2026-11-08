package team.codingforest.moyeota.faresplit.domain;

import java.time.Duration;
import java.time.Instant;

/**
 *  송금 요청 알림 차례 - 1번째는 정산을 시작할 때 보내고, 아직 안 보낸 사람에게 시작부터 INTERVAL 마다 다시 보낸다.
 *  만료(FareSplit.EXPIRES_AFTER) 시각과 그 뒤에는 차례가 없어 모두 LIMIT 번이다
 */
public final class TransferRequestSchedule {
    static final Duration INTERVAL = Duration.ofHours(4);
    // 시작 + 0·4·8·12·16·20시간
    public static final int LIMIT = 6;

    private TransferRequestSchedule() {
    }

    /** now 까지 차례가 지난 요청 수(0..LIMIT). 차례 시각과 같으면 지난 것으로 센다 */
    public static int passedCount(Instant createdAt, Instant now) {
        if(now.isBefore(createdAt)) return 0;

        return (int) Math.min(LIMIT, Duration.between(createdAt, now).dividedBy(INTERVAL) + 1);
    }
}
