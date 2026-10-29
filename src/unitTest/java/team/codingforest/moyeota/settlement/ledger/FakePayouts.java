package team.codingforest.moyeota.settlement.ledger;

import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Payouts;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

public class FakePayouts implements Payouts {
    private final Map<Long, Payout> store = new TreeMap<>();
    private long sequence = 0;
    private Supplier<Instant> clock = () -> Instant.now().truncatedTo(ChronoUnit.MICROS);

    public void setNow(Instant now) {
        this.clock = () -> now;
    }

    @Override
    public Payout save(Payout payout) {
        Long id = payout.getId() == null ? ++sequence : payout.getId();
        sequence = Math.max(sequence, id);

        if(payout.isActive() && hasOtherActive(payout.getSettlementId(), id)) {
            throw new DataIntegrityViolationException("uk_payout_settlement_active: settlementId=" + payout.getSettlementId());
        }

        Instant now = clock.get();
        Payout stored = store.get(id);
        Instant createdAt = stored != null ? stored.getCreatedAt() : payout.getCreatedAt() != null ? payout.getCreatedAt() : now;

        Payout saved = Payout.load(id, payout.getSettlementId(), payout.getAmount(), payout.getStatus(), payout.getPgId(),
                payout.getBankCode(), payout.getAccountNumber(), payout.getAccountHolder(), payout.getFailReason(), createdAt, now);
        store.put(id, saved);
        return copy(saved);
    }

    @Override
    public Optional<Payout> findById(Long id) {
        return Optional.ofNullable(store.get(id)).map(FakePayouts::copy);
    }

    @Override
    public Optional<Payout> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    public boolean existsActiveBySettlementId(Long settlementId) {
        return store.values().stream()
                .anyMatch(p -> p.getSettlementId().equals(settlementId) && p.isActive());
    }

    @Override
    public int countFailedBySettlementId(Long settlementId) {
        return (int) store.values().stream()
                .filter(p -> p.getSettlementId().equals(settlementId) && p.getStatus() == PayoutStatus.FAILED)
                .count();
    }

    @Override
    public Optional<Payout> findLatestBySettlementId(Long settlementId) {
        return store.values().stream()
                .filter(p -> p.getSettlementId().equals(settlementId))
                .max(Comparator.comparing(Payout::getId))
                .map(FakePayouts::copy);
    }

    @Override
    public List<Long> findIdsToReconcile(Instant requestedBefore) {
        return store.values().stream()
                .filter(p -> (p.getStatus() == PayoutStatus.REQUESTED && p.getCreatedAt().isBefore(requestedBefore))
                        || p.getStatus() == PayoutStatus.UNKNOWN)
                .map(Payout::getId)
                .sorted()
                .toList();
    }

    public List<Payout> findAll() {
        return store.values().stream().map(FakePayouts::copy).toList();
    }

    public List<Payout> findBySettlementId(Long settlementId) {
        return store.values().stream()
                .filter(p -> p.getSettlementId().equals(settlementId))
                .map(FakePayouts::copy)
                .toList();
    }

    public int count() {
        return store.size();
    }

    private boolean hasOtherActive(Long settlementId, Long id) {
        return store.values().stream()
                .anyMatch(p -> !p.getId().equals(id) && p.getSettlementId().equals(settlementId) && p.isActive());
    }

    private static Payout copy(Payout p) {
        return Payout.load(p.getId(), p.getSettlementId(), p.getAmount(), p.getStatus(), p.getPgId(), p.getBankCode(),
                p.getAccountNumber(), p.getAccountHolder(), p.getFailReason(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
