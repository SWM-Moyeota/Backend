package team.codingforest.moyeota.driver.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.driver.dto.DriverFcmToken;
import team.codingforest.moyeota.driver.exception.DriverErrorCode;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class DriverJpa implements Drivers {
    private final DriverJpaRepository delegate;

    @Override
    public Driver save(Driver driver) {
        if(driver.getId() != null) {
            DriverEntity entity = delegate.findById(driver.getId())
                    .orElseThrow(() -> new BusinessException(DriverErrorCode.DRIVER_NOT_FOUND));

            entity.update(driver);
            delegate.save(entity);
            return entity.toDomain();
        }

        DriverEntity entity = DriverEntity.from(driver);
        delegate.save(entity);
        return entity.toDomain();
    }

    @Override
    public Optional<Driver> findById(Long id) {
        return delegate.findById(id)
                .map(DriverEntity::toDomain);
    }

    @Override
    public Optional<Driver> findByIdForUpdate(Long id) {
        return delegate.findByIdForUpdate(id)
                .map(DriverEntity::toDomain);
    }

    @Override
    public Optional<Driver> findByUserId(Long userId) {
        return delegate.findByUserId(userId)
                .map(DriverEntity::toDomain);
    }

    @Override
    public Optional<Driver> findByUserIdForUpdate(Long userId) {
        return delegate.findByUserIdForUpdate(userId)
                .map(DriverEntity::toDomain);
    }

    @Override
    public Map<Long, String> findFcmTokens(Collection<Long> ids) {
        if(ids.isEmpty()) return Map.of();
        return delegate.findFcmTokens(ids).stream().collect(Collectors.toMap(DriverFcmToken::driverId, DriverFcmToken::fcmToken));
    }

    @Override
    public int deleteWithdrawnBefore(Instant before) {
        List<DriverEntity> expired = delegate.findWithdrawnBefore(before);
        delegate.deleteAll(expired);
        return expired.size();
    }
}
