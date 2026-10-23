package team.codingforest.moyeota.driver;

import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class DriverJpaTest implements Drivers {
    private final Map<Long, Driver> store = new HashMap<>();
    private Long sequence = 0L;

    @Override
    public Driver save(Driver driver) {
        Long id = driver.getId() != null ? driver.getId() : ++sequence;
        Driver saved = Driver.load(id, driver.getUserId(), driver.getQualificationNumber(),
                driver.getVerifiedAt(), driver.getBankAccount(), driver.getVehicle(),
                driver.getSetting(), driver.getStatus(), driver.getFcmToken(), driver.getDeletedAt());

        store.put(id, saved);
        return saved;
    }

    @Override
    public Optional<Driver> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Driver> findByUserId(Long userId) {
        return store.values().stream()
                .filter(d -> d.getUserId().equals(userId))
                .findFirst();
    }

    @Override
    public Map<Long, String> findFcmTokens(Collection<Long> ids) {
        Map<Long, String> tokens = new HashMap<>();
        for(Long id : ids) {
            Driver driver = store.get(id);
            if(driver != null && driver.getFcmToken() != null && !driver.getFcmToken().isEmpty()) tokens.put(id, driver.getFcmToken());
        }
        return tokens;
    }

    @Override
    public Optional<Driver> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    public Optional<Driver> findByUserIdForUpdate(Long userId) {
        return findByUserId(userId);
    }

    @Override
    public int deleteWithdrawnBefore(Instant before) {
        int size = store.size();
        store.values().removeIf(d -> d.isWithdrawn() && d.getDeletedAt().isBefore(before));
        return size - store.size();
    }
}