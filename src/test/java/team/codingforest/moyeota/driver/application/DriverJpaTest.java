package team.codingforest.moyeota.driver.application;

import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class DriverJpaTest implements Drivers {
    private final Map<Long, Driver> store = new HashMap<>();
    private Long sequence = 0L;

    @Override
    public Driver save(Driver driver) {
        Long id = driver.getId() != null ? driver.getId() : ++sequence;
        Driver saved = Driver.restore(id, driver.getUserId(), driver.getQualificationNumber(),
                driver.getVerifiedAt(), driver.getBankAccount(), driver.getVehicle(),
                driver.getSetting(), driver.getStatus(), driver.getFcmToken());

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
}