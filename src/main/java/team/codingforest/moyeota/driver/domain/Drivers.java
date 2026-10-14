package team.codingforest.moyeota.driver.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface Drivers {
    Driver save(Driver driver);
    Optional<Driver> findById(Long id);
    Optional<Driver> findByUserId(Long userId);
    Map<Long, String> findFcmTokens(Collection<Long> ids);
    void deleteByUserId(Long userId);
}
