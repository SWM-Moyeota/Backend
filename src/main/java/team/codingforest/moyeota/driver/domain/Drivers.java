package team.codingforest.moyeota.driver.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface Drivers {
    Duration RETENTION = Duration.ofDays(180);

    Driver save(Driver driver);
    Optional<Driver> findById(Long id);
    Optional<Driver> findByIdForUpdate(Long id);
    Optional<Driver> findByUserId(Long userId);
    Optional<Driver> findByUserIdForUpdate(Long userId);
    Map<Long, String> findFcmTokens(Collection<Long> ids);
    int deleteWithdrawnBefore(Instant before);
}
