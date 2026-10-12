package team.codingforest.moyeota.user.deletion.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.common.crypto.FieldHasher;
import team.codingforest.moyeota.user.deletion.domain.UserDeletions;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class UserDeletionJpa implements UserDeletions {
    private final UserDeletionRepository repository;
    private final FieldHasher hasher;

    // id 가 이미 있으면 save 가 merge 로 동작해 deleted_at 만 갱신된다. H2 가 ON CONFLICT 를 몰라 네이티브 upsert 대신 쓴다
    @Override
    public void record(String phoneNumber, Instant deletedAt) {
        repository.save(UserDeletionEntity.of(hasher.hash(phoneNumber), deletedAt));
    }

    @Override
    public boolean existsDeletedSince(String phoneNumber, Instant since) {
        return repository.existsByPhoneHashAndDeletedAtAfter(hasher.hash(phoneNumber), since);
    }
}
