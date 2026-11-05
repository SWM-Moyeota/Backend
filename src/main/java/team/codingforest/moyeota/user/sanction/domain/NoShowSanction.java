package team.codingforest.moyeota.user.sanction.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 *  한 사용자의 노쇼 제재. 제한 끝 시각만 users 행에 저장하고, 노쇼 횟수는 저장하지 않고 노쇼 기록(NoShow)에서 그때그때 센다.
 *  값 객체라 바꾸는 메소드는 새 객체를 돌려준다
 *
 *  @param restrictedUntil 이 시각까지 방 만들기·참여 불가. null 이면 제한 없음(지난 시각이면 이미 풀린 것)
 */
public record NoShowSanction(Instant restrictedUntil) {
    public static final Duration RESTRICTION_PER_COUNT = Duration.ofHours(24);
    public static final NoShowSanction NONE = new NoShowSanction(null);

    /** now 에 세는 노쇼 수 - 무효가 아니고 약속 시간이 (now - 30일, ...] 안인 노쇼 */
    public static int count(Collection<NoShow> noShows, Instant now) {
        return (int) noShows.stream().filter(noShow -> noShow.countsAt(now)).count();
    }

    /**
     *  노쇼 1건을 기록한 뒤의 제재. noShows 는 이번 노쇼까지 담은 이 사용자의 노쇼다.
     *  제한은 약속 시간부터 (지금 세는 노쇼 수 × 24시간)까지. 이미 더 늦게 끝나는 제한이 있으면(다른 방의 노쇼) 그쪽을 남긴다
     */
    public NoShowSanction record(Instant meetingAt, Collection<NoShow> noShows, Instant now) {
        Objects.requireNonNull(meetingAt, "노쇼가 난 방의 약속 시간이 없습니다");

        Instant until = meetingAt.plus(RESTRICTION_PER_COUNT.multipliedBy(count(noShows, now)));

        if(restrictedUntil != null && restrictedUntil.isAfter(until)) until = restrictedUntil;

        return new NoShowSanction(until);
    }

    /** 노쇼 1건 취소(운영자가 이의를 받아들임) - 지금 걸린 제한을 푼다 */
    public NoShowSanction cancel() {
        return NONE;
    }

    /**
     *  now 기준으로 아직 걸려 있는 제한의 끝 시각. 제한이 없거나 이미 지났으면 empty
     */
    public Optional<Instant> activeRestrictionUntil(Instant now) {
        return Optional.ofNullable(restrictedUntil).filter(until -> until.isAfter(now));
    }
}
