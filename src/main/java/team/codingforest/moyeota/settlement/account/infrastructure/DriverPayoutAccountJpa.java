package team.codingforest.moyeota.settlement.account.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccount;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccounts;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DriverPayoutAccountJpa implements DriverPayoutAccounts {
    private final DriverPayoutAccountJpaRepository repository;

    /**
     * 새 계좌면 넣고, 있던 계좌면 찾아서 바꿈
     * 셀러 등록은 토스 호출을 트랜잭션 밖에 두고 앞뒤로 따로 저장하므로, 서비스 트랜잭션 없이 불려도 UPDATE 가 나가게 여기서 트랜잭션을 염
     *
     * @param account 저장할 지급 계좌
     * @return 저장된 지급 계좌
     */
    @Override
    @Transactional
    public DriverPayoutAccount save(DriverPayoutAccount account) {
        if(account.getId() == null) {
            return repository.save(DriverPayoutAccountEntity.from(account)).toDomain();
        }

        return repository.findById(account.getId())
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 지급 계좌입니다. id=" + account.getId()))
                .update(account)
                .toDomain();
    }

    @Override
    public void delete(DriverPayoutAccount account) {
        repository.deleteById(account.getId());
    }

    @Override
    public Optional<DriverPayoutAccount> findByDriverId(Long taxiDriverId) {
        return repository.findByTaxiDriverId(taxiDriverId).map(DriverPayoutAccountEntity::toDomain);
    }

    @Override
    public Optional<DriverPayoutAccount> findBySellerId(String sellerId) {
        return repository.findBySellerId(sellerId).map(DriverPayoutAccountEntity::toDomain);
    }
}
