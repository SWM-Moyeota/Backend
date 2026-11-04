package team.codingforest.moyeota.user.sanction.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 *  한 사용자의 노쇼 제재 상태(users 행에 같이 저장). 값 객체라 바꾸는 메소드는 새 객체를 돌려준다.
 *  누적 횟수는 노쇼 없이 30일이 지날 때마다 1 줄어든다(마지막 노쇼·마지막 감소에서 센다). 줄어든 값은 저장하지 않고
 *  effectiveCount 로 그때그때 계산한다 - 줄이는 스케줄러가 없다
 *
 *  @param count           마지막으로 저장한 누적 횟수(감소 반영 전)
 *  @param countUpdatedAt  count 를 마지막으로 바꾼 시각. 감소 계산의 기준. 노쇼가 없었으면 null
 *  @param restrictedUntil 이 시각까지 방 만들기·참여 불가. null 이면 제한 없음(지난 시각이면 이미 풀린 것)
 */
public record NoShowSanction(int count, Instant countUpdatedAt, Instant restrictedUntil) {
    public static final Duration DECAY_PERIOD = Duration.ofDays(30);
    public static final Duration RESTRICTION_PER_COUNT = Duration.ofHours(24);
    public static final NoShowSanction NONE = new NoShowSanction(0, null, null);

    public NoShowSanction {
        if(count < 0) throw new IllegalArgumentException("노쇼 누적 횟수는 0 이상이어야 합니다: " + count);
    }

    /**
     *  감소를 반영한 누적 횟수 = max(0, count - (countUpdatedAt 부터 지난 30일 수)).
     *  기준 시각이 없거나 now 가 기준보다 이르면(서버 시계 어긋남) 줄이지 않는다
     */
    public int effectiveCount(Instant now) {
        if(count == 0 || countUpdatedAt == null || now.isBefore(countUpdatedAt)) return count;

        long periods = Duration.between(countUpdatedAt, now).dividedBy(DECAY_PERIOD);

        return (int) Math.max(0, count - periods);
    }

    /**
     *  노쇼 1번 기록 - 누적 횟수 = 감소 반영 횟수 + 1, 기준 시각 = now.
     *  제한은 약속 시간부터 (누적 횟수 × 24시간)까지. 이미 더 늦게 끝나는 제한이 있으면(다른 방의 노쇼) 그쪽을 남긴다
     */
    public NoShowSanction record(Instant meetingAt, Instant now) {
        Objects.requireNonNull(meetingAt, "노쇼가 난 방의 약속 시간이 없습니다");

        int next = effectiveCount(now) + 1;
        Instant until = meetingAt.plus(RESTRICTION_PER_COUNT.multipliedBy(next));

        if(restrictedUntil != null && restrictedUntil.isAfter(until)) until = restrictedUntil;

        return new NoShowSanction(next, now, until);
    }

    /**
     *  노쇼 1번 취소(운영자가 이의를 받아들임) - 누적 횟수 = 감소 반영 횟수 - 1(0 아래로 내려가지 않음), 기준 시각 = now, 지금 걸린 제한은 푼다
     */
    public NoShowSanction cancel(Instant now) {
        return new NoShowSanction(Math.max(0, effectiveCount(now) - 1), now, null);
    }

    /**
     *  now 기준으로 아직 걸려 있는 제한의 끝 시각. 제한이 없거나 이미 지났으면 empty
     */
    public Optional<Instant> activeRestrictionUntil(Instant now) {
        return Optional.ofNullable(restrictedUntil).filter(until -> until.isAfter(now));
    }
}
