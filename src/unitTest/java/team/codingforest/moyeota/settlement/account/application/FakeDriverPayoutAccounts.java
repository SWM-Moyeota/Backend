package team.codingforest.moyeota.settlement.account.application;

import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccount;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccounts;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * DB 처럼 저장할 때 복사본을 남겨, save 하지 않은 변경은 반영되지 않게 함
 * 기사 UNIQUE 와 없는 행 UPDATE 실패도 실제 저장소와 같게 흉내 냄
 */
public class FakeDriverPayoutAccounts implements DriverPayoutAccounts {
    private final Map<Long, DriverPayoutAccount> rows = new LinkedHashMap<>();
    private long sequence = 30;
    private DriverPayoutAccount racing;

    /**
     * 이미 있는 행을 그대로 넣음. id 와 생성 시각을 테스트가 정함
     *
     * @param account 넣을 행
     * @return 이 저장소
     */
    public FakeDriverPayoutAccounts put(DriverPayoutAccount account) {
        rows.put(account.getId(), copy(account, account.getCreatedAt(), account.getUpdatedAt()));
        sequence = Math.max(sequence, account.getId());
        return this;
    }

    /**
     * 다음 조회는 빈 결과를 주고 그 사이 다른 요청이 같은 기사 행을 넣은 것처럼 만듦
     *
     * @param account 다른 요청이 넣은 행
     */
    public void insertedByAnotherRequestAfterNextLookup(DriverPayoutAccount account) {
        racing = account;
    }

    public Optional<DriverPayoutAccount> row(Long id) {
        return Optional.ofNullable(rows.get(id)).map(found -> copy(found, found.getCreatedAt(), found.getUpdatedAt()));
    }

    public int size() {
        return rows.size();
    }

    @Override
    public DriverPayoutAccount save(DriverPayoutAccount account) {
        Instant now = Instant.now();

        if(account.getId() == null) {
            boolean duplicated = rows.values().stream().anyMatch(row -> row.getTaxiDriverId().equals(account.getTaxiDriverId()));
            if(duplicated) throw new DataIntegrityViolationException("uk_driver_payout_account_driver");

            DriverPayoutAccount inserted = DriverPayoutAccount.load(++sequence, account.getTaxiDriverId(), account.getBusinessType(),
                    account.getBankCode(), account.getMaskedAccountNumber(), account.getAccountHolder(), account.getSellerId(),
                    account.getSellerStatus(), now, now);
            rows.put(inserted.getId(), inserted);
            return copy(inserted, now, now);
        }

        DriverPayoutAccount existing = rows.get(account.getId());
        if(existing == null) throw new IllegalStateException("존재하지 않는 지급 계좌입니다. id=" + account.getId());

        DriverPayoutAccount updated = copy(account, existing.getCreatedAt(), now);
        rows.put(updated.getId(), updated);
        return copy(updated, updated.getCreatedAt(), updated.getUpdatedAt());
    }

    @Override
    public void delete(DriverPayoutAccount account) {
        rows.remove(account.getId());
    }

    @Override
    public Optional<DriverPayoutAccount> findByDriverId(Long taxiDriverId) {
        if(racing != null) {
            put(racing);
            racing = null;
            return Optional.empty();
        }

        return rows.values().stream()
                .filter(row -> row.getTaxiDriverId().equals(taxiDriverId))
                .findFirst()
                .map(found -> copy(found, found.getCreatedAt(), found.getUpdatedAt()));
    }

    @Override
    public Optional<DriverPayoutAccount> findBySellerId(String sellerId) {
        return rows.values().stream()
                .filter(row -> sellerId.equals(row.getSellerId()))
                .findFirst()
                .map(found -> copy(found, found.getCreatedAt(), found.getUpdatedAt()));
    }

    private static DriverPayoutAccount copy(DriverPayoutAccount account, Instant createdAt, Instant updatedAt) {
        return DriverPayoutAccount.load(account.getId(), account.getTaxiDriverId(), account.getBusinessType(), account.getBankCode(),
                account.getMaskedAccountNumber(), account.getAccountHolder(), account.getSellerId(), account.getSellerStatus(),
                createdAt, updatedAt);
    }
}
