package team.codingforest.moyeota.user.sanction.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NoShowRepository extends JpaRepository<NoShowEntity, Long> {
    boolean existsByUserIdAndPartyId(Long userId, Long partyId);

    Optional<NoShowEntity> findByUserIdAndPartyId(Long userId, Long partyId);

    List<NoShowEntity> findByUserIdAndMeetingAtAfter(Long userId, Instant meetingAt);
}
