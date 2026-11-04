package team.codingforest.moyeota.matching.party.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 *  알림 방해 금지 시간 - 한국 시각 밤 23:00 부터 아침 08:00 전까지.
 *  이 시간에는 늦게 받아도 되는 알림(출발 재촉·노쇼 알림)을 보내지 않고 나중에 몰아 보내지도 않는다.
 *  약속 시각 알림은 약속 자체가 밤일 수 있어 이 시간을 따르지 않는다
 */
public final class QuietHours {
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final LocalTime START = LocalTime.of(23, 0);
    static final LocalTime END = LocalTime.of(8, 0);

    private QuietHours() {
    }

    /** 23:00:00 은 방해 금지, 08:00:00 은 아니다 */
    public static boolean contains(Instant now) {
        LocalTime time = now.atZone(ZONE).toLocalTime();

        return !time.isBefore(START) || time.isBefore(END);
    }
}
