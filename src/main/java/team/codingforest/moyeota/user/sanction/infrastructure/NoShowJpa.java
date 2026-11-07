package team.codingforest.moyeota.user.sanction.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.sanction.domain.NoShow;
import team.codingforest.moyeota.user.sanction.domain.NoShows;

import java.time.Instant;
import java.util.List;

/**
 *  같은 사용자·방이 이미 있는지 먼저 보고 더한다 - 부르는 쪽이 users 행을 잠가 같은 사용자의 기록이 줄을 서므로 둘 다 더해지지 않는다.
 *  (user_id, match_id) 유니크 제약은 잠금 없이 들어온 경우를 막는 마지막 장치라, 위반하면 부른 쪽 트랜잭션이 실패한다
 */
@Repository
@RequiredArgsConstructor
public class NoShowJpa implements NoShows {
    private final NoShowRepository repository;

    @Override
    public boolean add(Long userId, Long partyId, Instant meetingAt) {
        if(repository.existsByUserIdAndPartyId(userId, partyId)) return false;

        repository.save(NoShowEntity.of(userId, partyId, meetingAt));
        return true;
    }

    // 더티체킹이라 트랜잭션 안에서 불러야 한다
    @Override
    public boolean markVoided(Long userId, Long partyId, Instant voidedAt) {
        return repository.findByUserIdAndPartyId(userId, partyId)
                .map(entity -> entity.markVoided(voidedAt))
                .orElse(false);
    }

    @Override
    public List<NoShow> findUnexpired(Long userId, Instant now) {
        return repository.findByUserIdAndMeetingAtAfter(userId, NoShow.expiredUntil(now)).stream()
                .map(NoShowEntity::toDomain)
                .toList();
    }
}
