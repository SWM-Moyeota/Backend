package team.codingforest.moyeota.user.deletion.domain;

import java.time.Instant;

public interface UserDeletions {
    void record(String normalizedPhoneNumber, Instant deletedAt);
    boolean existsDeletedSince(String normalizedPhoneNumber, Instant since);
}
