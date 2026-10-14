package team.codingforest.moyeota.driver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.user.api.DriverWithdrawal;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
class DriverWithdrawalService implements DriverWithdrawal {
    private final Drivers drivers;

    @Override
    @Transactional
    public void withdraw(Long userId, Instant withdrawnAt) {
        drivers.findByUserIdForUpdate(userId)
                .filter(driver -> !driver.isWithdrawn())
                .ifPresent(driver -> {
                    driver.withdraw(withdrawnAt);
                    drivers.save(driver);
                    log.info("기사 탈퇴 처리 driverId={}, userId={}", driver.getId(), userId);
                });
    }
}
