package team.codingforest.moyeota.common.time;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 *  알림 방해 금지 시간 - 한국 시각 밤 23:00 부터 아침 08:00 전까지.
 */
public record QuietHours() {
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final LocalTime START = LocalTime.of(23, 0);
    static final LocalTime END = LocalTime.of(8, 0);

    public static boolean contains(Instant now) {
        LocalTime time = now.atZone(ZONE).toLocalTime();

        return !time.isBefore(START) || time.isBefore(END);
    }
}
