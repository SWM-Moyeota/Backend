package team.codingforest.moyeota.user.api;

import java.time.Instant;
import java.util.Optional;

/**
 * 노쇼 제재 창구. 제재 상태(누적 횟수·제한 끝 시각)는 users 행에 있고, 노쇼를 판정하는 matching 이 부른다.
 * 누적 횟수는 노쇼 없이 30일이 지날 때마다 1 줄어든다 - 저장하지 않고 읽거나 바꿀 때 계산한다.
 * 기록·취소는 부른 쪽 트랜잭션에 같이 묶인다(노쇼 저장과 제재가 함께 커밋되거나 함께 되돌려진다)
 */
public interface NoShowPenalty {
    /**
     * 노쇼 1번을 더한다. 누적 횟수 = 감소를 반영한 횟수 + 1, 제한은 약속 시간부터 (누적 횟수 × 24시간)까지.
     * 이미 더 늦게 끝나는 제한이 걸려 있으면 그쪽을 남긴다. 사용자가 없으면 아무것도 하지 않는다
     *
     * @param userId 노쇼한 사용자의 내부 번호
     * @param meetingAt 노쇼가 난 방의 약속 시간(match_room.departure_at) - 제한은 기록한 시각이 아니라 여기서부터 센다
     * @param now 기록 시각 - 감소 계산의 새 기준
     */
    void record(Long userId, Instant meetingAt, Instant now);

    /**
     * 운영자가 이의를 받아들여 노쇼 1번을 지운다. 누적 횟수 = 감소를 반영한 횟수 - 1(0 아래로 내려가지 않음), 지금 걸린 제한은 풀린다.
     * 사용자가 없으면 아무것도 하지 않는다
     *
     * @param userId 노쇼가 무효로 돌아간 사용자의 내부 번호
     * @param now 처리 시각 - 감소 계산의 새 기준
     */
    void cancel(Long userId, Instant now);

    /**
     * 방 만들기·참여가 막혀 있는지
     *
     * @param userId 확인할 사용자의 내부 번호
     * @param now 기준 시각
     * @return 제한이 끝나는 시각. 제한이 없거나 이미 끝났으면(사용자가 없어도) empty
     */
    Optional<Instant> findRestrictedUntil(Long userId, Instant now);
}
