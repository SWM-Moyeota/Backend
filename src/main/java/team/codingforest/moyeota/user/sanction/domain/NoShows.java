package team.codingforest.moyeota.user.sanction.domain;

import java.time.Instant;
import java.util.List;

/**
 *  노쇼 기록 저장소(user_no_show). 사용자·방마다 한 건이다.
 *  기록·무효는 같은 사용자의 users 행을 잠근 트랜잭션 안에서 부른다(NoShowSanctions.findForUpdate)
 */
public interface NoShows {
    /** 노쇼 1건을 더한다. 같은 사용자·방의 노쇼가 이미 있으면(무효로 돌아간 것 포함) 더하지 않고 false */
    boolean add(Long userId, Long partyId, Instant meetingAt);

    /** 노쇼를 무효로 돌린다. 없거나 이미 무효면 false */
    boolean markVoided(Long userId, Long partyId, Instant voidedAt);

    /** now 에 아직 만료되지 않은 노쇼(무효로 돌아간 것 포함) */
    List<NoShow> findUnexpired(Long userId, Instant now);
}
