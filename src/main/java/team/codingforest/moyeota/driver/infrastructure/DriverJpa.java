package team.codingforest.moyeota.driver.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.driver.domain.exception.DriverErrorCode;

import java.util.Optional;

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
    public Optional<Driver> findByUserId(Long userId) {
        return delegate.findByUserId(userId)
                .map(DriverEntity::toDomain);
    }
}
