package team.codingforest.moyeota.settlement.ledger;

import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutAccount;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutAccounts;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FakePayoutAccounts implements PayoutAccounts {
    public static final BankCode SAMPLE_BANK = BankCode.values()[0];
    public static final String SAMPLE_ACCOUNT_NUMBER = "110123456789";
    public static final String SAMPLE_ACCOUNT_HOLDER = "김기사";

    private final Map<Long, PayoutAccount> accounts = new HashMap<>();
    private int lookups = 0;

    public static PayoutAccount sampleAccount() {
        return new PayoutAccount(SAMPLE_BANK, SAMPLE_ACCOUNT_NUMBER, SAMPLE_ACCOUNT_HOLDER);
    }

    public FakePayoutAccounts register(Long taxiDriverId, PayoutAccount account) {
        accounts.put(taxiDriverId, account);
        return this;
    }

    public FakePayoutAccounts register(Long taxiDriverId) {
        return register(taxiDriverId, sampleAccount());
    }

    @Override
    public Optional<PayoutAccount> findByDriverId(Long taxiDriverId) {
        lookups++;
        return Optional.ofNullable(accounts.get(taxiDriverId));
    }

    public int lookups() {
        return lookups;
    }
}
