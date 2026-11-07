package team.codingforest.moyeota.user.api;

import java.time.Instant;
import java.util.Optional;

/**
 * 노쇼 제재 창구. 노쇼는 건마다 user_no_show 에 남고 제한 끝 시각은 users 행에 있다. 노쇼를 판정하는 matching 이 부른다.
 * 노쇼 1건은 그 방의 약속 시간부터 30일 동안만 센다.
 * 기록·취소는 부른 쪽 트랜잭션에 같이 묶인다(노쇼 저장과 제재가 함께 커밋되거나 함께 되돌려진다)
 */
public interface NoShowPenalty {
    /**
     * 노쇼 1건을 기록한다. 제한은 약속 시간부터 (이번 노쇼까지 지금 세는 노쇼 수 × 24시간)까지.
     * 이미 더 늦게 끝나는 제한이 걸려 있으면 그쪽을 남긴다. 같은 방의 노쇼가 이미 있거나 사용자가 없으면 아무것도 하지 않는다
     *
     * @param userId 노쇼한 사용자의 내부 번호
     * @param partyId 노쇼가 난 방(match_room.id)
     * @param meetingAt 그 방의 약속 시간(match_room.departure_at) - 제한과 30일 만료를 기록한 시각이 아니라 여기서부터 센다
     * @param now 기록 시각
     */
    void record(Long userId, Long partyId, Instant meetingAt, Instant now);

    /**
     * 운영자가 이의를 받아들여 그 방의 노쇼를 무효로 돌린다. 무효가 된 노쇼는 세지 않고, 지금 걸린 제한은 풀린다.
     * 그 방의 노쇼가 없거나 이미 무효거나 사용자가 없으면 아무것도 하지 않는다
     *
     * @param userId 노쇼가 무효로 돌아간 사용자의 내부 번호
     * @param partyId 노쇼가 난 방(match_room.id)
     * @param now 처리 시각
     */
    void cancel(Long userId, Long partyId, Instant now);

    /**
     * 방 만들기·참여가 막혀 있는지
     *
     * @param userId 확인할 사용자의 내부 번호
     * @param now 기준 시각
     * @return 제한이 끝나는 시각. 제한이 없거나 이미 끝났으면(사용자가 없어도) empty
     */
    Optional<Instant> findRestrictedUntil(Long userId, Instant now);
}
