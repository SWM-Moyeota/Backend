package team.codingforest.moyeota.faresplit.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 *  송금 요청 알림 차례 - 모두 LIMIT 번. 1번째는 정산을 시작할 때 보내고, 아직 안 보낸 사람에게
 *  시작 1시간 뒤(2번째), 시작 다음 날 10시(3번째)에 다시 보낸다. 날짜와 10시는 한국 시각이다.
 *  마지막 차례도 정산이 만료(FareSplit.EXPIRES_AFTER)되기 전이어야 한다.
 *  재요청 대상 조회(FareSplitJpaRepository.findTransferRequestTargetIds)가 차례마다 기준 시각을 하나씩 받으므로 차례를 바꾸면 쿼리도 함께 바꾼다
 */
public final class TransferRequestSchedule {
    public static final int LIMIT = 3;
    static final Duration SECOND_AFTER = Duration.ofHours(1);
    static final LocalTime DAILY_AT = LocalTime.of(10, 0);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private TransferRequestSchedule() {
    }

    /** nth 번째(1..LIMIT) 요청 시각 */
    public static Instant dueOf(Instant createdAt, int nth) {
        return switch(nth) {
            case 1 -> createdAt;
            case 2 -> createdAt.plus(SECOND_AFTER);
            case 3 -> createdAt.atZone(ZONE).toLocalDate().plusDays(1).atTime(DAILY_AT).atZone(ZONE).toInstant();
            default -> throw new IllegalArgumentException("송금 요청은 1.." + LIMIT + " 번째까지다: " + nth);
        };
    }

    /** now 까지 차례가 지난 요청 수(0..LIMIT) */
    public static int passedCount(Instant createdAt, Instant now) {
        int passed = 0;

        while(passed < LIMIT && !now.isBefore(dueOf(createdAt, passed + 1))) passed++;

        return passed;
    }

    /**
     *  재요청 대상 조회의 기준 - now 에 nth 번째(2..LIMIT) 차례가 지난 정산은 이 시각보다 먼저 시작했다.
     *  2번째는 시작 + 1시간이 now 와 똑같은 정산을 다음 회차로 미룬다(1분 뒤 다시 고른다)
     */
    public static Instant startedBefore(int nth, Instant now) {
        return switch(nth) {
            case 2 -> now.minus(SECOND_AFTER);
            // now 까지 지난 마지막 10시가 d 일이면 (시작일 + 1) 10시 <= now ⇔ 시작일 <= d - 1 ⇔ 시작 < d 0시
            case 3 -> lastDailyDate(now).atStartOfDay(ZONE).toInstant();
            default -> throw new IllegalArgumentException("재요청은 2.." + LIMIT + " 번째다: " + nth);
        };
    }

    private static LocalDate lastDailyDate(Instant now) {
        ZonedDateTime local = now.atZone(ZONE);

        return local.toLocalTime().isBefore(DAILY_AT) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
    }
}
