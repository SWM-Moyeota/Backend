package team.codingforest.moyeota.user.passwordreset.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
import team.codingforest.moyeota.user.passwordreset.domain.*;

@Repository @RequiredArgsConstructor
public class ResetGrantsJpa implements ResetGrants {
 private final ResetGrantRepository repository;
 public void save(ResetGrant grant) { repository.save(ResetGrantEntity.from(grant)); }
 public Optional<Long> findOwnerId(String hash) { return repository.findOwnerId(hash); }
 public Optional<ResetGrant> lockByTokenHash(String hash) { return repository.lockByTokenHash(hash).map(ResetGrantEntity::toDomain); }
 public void consumeAll(Long id, Instant now) { repository.consumeAll(id, now); }
 @Scheduled(fixedDelayString="${moyeota.password-reset.cleanup-delay-ms:3600000}")
 @Transactional
 public void cleanExpired() { repository.deleteExpired(Instant.now().minusSeconds(86400)); }
}
