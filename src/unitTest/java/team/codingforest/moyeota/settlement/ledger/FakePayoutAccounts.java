package team.codingforest.moyeota.settlement.ledger;

import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.account.domain.BusinessType;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccount;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccounts;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FakePayoutAccounts implements DriverPayoutAccounts {
    public static final BankCode SAMPLE_BANK = BankCode.KOOKMIN;
    public static final String SAMPLE_MASKED_ACCOUNT_NUMBER = "********6789";
    public static final String SAMPLE_ACCOUNT_HOLDER = "김기사";
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-01T00:00:00Z");

    private final Map<Long, DriverPayoutAccount> accounts = new HashMap<>();
    private int lookups = 0;

    public static String sellerOf(Long taxiDriverId) {
        return "seller_" + taxiDriverId;
    }

    public static DriverPayoutAccount sampleAccount(Long taxiDriverId, SellerStatus status) {
        String sellerId = status == null ? null : sellerOf(taxiDriverId);
        return DriverPayoutAccount.load(taxiDriverId + 1000, taxiDriverId, BusinessType.CORPORATE, SAMPLE_BANK, SAMPLE_MASKED_ACCOUNT_NUMBER,
                SAMPLE_ACCOUNT_HOLDER, sellerId, status, REGISTERED_AT, REGISTERED_AT);
    }

    public FakePayoutAccounts register(DriverPayoutAccount account) {
        accounts.put(account.getTaxiDriverId(), account);
        return this;
    }

    /**
     * 지급받을 수 있게 승인된 셀러로 등록함
     *
     * @param taxiDriverId 기사 id
     * @return 이 저장소
     */
    public FakePayoutAccounts register(Long taxiDriverId) {
        return register(sampleAccount(taxiDriverId, SellerStatus.APPROVED));
    }

    /**
     * 셀러 상태를 정해 등록함. null 이면 셀러 등록이 끝나지 않은 계좌
     *
     * @param taxiDriverId 기사 id
     * @param status       셀러 상태
     * @return 이 저장소
     */
    public FakePayoutAccounts register(Long taxiDriverId, SellerStatus status) {
        return register(sampleAccount(taxiDriverId, status));
    }

    @Override
    public DriverPayoutAccount save(DriverPayoutAccount account) {
        register(account);
        return account;
    }

    @Override
    public void delete(DriverPayoutAccount account) {
        accounts.remove(account.getTaxiDriverId());
    }

    @Override
    public Optional<DriverPayoutAccount> findByDriverId(Long taxiDriverId) {
        lookups++;
        return Optional.ofNullable(accounts.get(taxiDriverId));
    }

    @Override
    public Optional<DriverPayoutAccount> findBySellerId(String sellerId) {
        return accounts.values().stream()
                .filter(account -> sellerId.equals(account.getSellerId()))
                .findFirst();
    }

    public int lookups() {
        return lookups;
    }
}
