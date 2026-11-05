package team.codingforest.moyeota.user.sanction.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 *  노쇼 1건(user_no_show 행). 그 방의 약속 시간부터 30일 동안만 센다 - 정확히 30일이 된 순간부터는 세지 않는다.
 *  운영자가 이의를 받아들여 무효로 돌린 노쇼도 세지 않는다
 *
 *  @param meetingAt 노쇼가 난 방의 약속 시간
 *  @param voidedAt  무효로 돌린 시각. null 이면 유효한 노쇼
 */
public record NoShow(Instant meetingAt, Instant voidedAt) {
    public static final Duration EXPIRY = Duration.ofDays(30);

    public NoShow {
        Objects.requireNonNull(meetingAt, "노쇼가 난 방의 약속 시간이 없습니다");
    }

    /** 약속 시간이 이 시각 이하인 노쇼는 now 에 이미 만료됐다 */
    public static Instant expiredUntil(Instant now) {
        return now.minus(EXPIRY);
    }

    public boolean countsAt(Instant now) {
        return voidedAt == null && meetingAt.isAfter(expiredUntil(now));
    }
}
