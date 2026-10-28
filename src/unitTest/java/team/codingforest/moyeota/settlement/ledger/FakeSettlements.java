package team.codingforest.moyeota.settlement.ledger;

import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Settlements;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class FakeSettlements implements Settlements {
    private final Map<Long, Settlement> store = new TreeMap<>();
    private long sequence = 0;

    @Override
    public Settlement save(Settlement settlement) {
        Long id = settlement.getId() == null ? ++sequence : settlement.getId();
        sequence = Math.max(sequence, id);

        if(hasOtherSamePeriod(settlement.getTaxiDriverId(), settlement.getPeriodStart(), id)) {
            throw new DataIntegrityViolationException("uk_settlement_driver_period: taxiDriverId=" + settlement.getTaxiDriverId()
                    + ", periodStart=" + settlement.getPeriodStart());
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Settlement stored = store.get(id);
        Instant createdAt = stored != null ? stored.getCreatedAt() : now;

        Settlement saved = Settlement.load(id, settlement.getTaxiDriverId(), settlement.getPeriodStart(), settlement.getPeriodEnd(),
                settlement.getAmount(), settlement.getStatus(), createdAt, now);
        store.put(id, saved);
        return copy(saved);
    }

    @Override
    public Optional<Settlement> findById(Long id) {
        return Optional.ofNullable(store.get(id)).map(FakeSettlements::copy);
    }

    @Override
    public Optional<Settlement> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    public Optional<Settlement> findByDriverAndPeriod(Long taxiDriverId, SettlementPeriod period) {
        return store.values().stream()
                .filter(s -> s.getTaxiDriverId().equals(taxiDriverId) && s.getPeriodStart().equals(period.start()))
                .findFirst()
                .map(FakeSettlements::copy);
    }

    @Override
    public List<Long> findUnpaidIds() {
        return store.values().stream()
                .filter(s -> s.getStatus() == SettlementStatus.CONFIRMED
                        || s.getStatus() == SettlementStatus.HOLD
                        || s.getStatus() == SettlementStatus.FAILED)
                .map(Settlement::getId)
                .sorted()
                .toList();
    }

    public List<Settlement> findAll() {
        return store.values().stream().map(FakeSettlements::copy).toList();
    }

    public int count() {
        return store.size();
    }

    private boolean hasOtherSamePeriod(Long taxiDriverId, Instant periodStart, Long id) {
        return store.values().stream()
                .anyMatch(s -> !s.getId().equals(id) && s.getTaxiDriverId().equals(taxiDriverId) && s.getPeriodStart().equals(periodStart));
    }

    private static Settlement copy(Settlement s) {
        return Settlement.load(s.getId(), s.getTaxiDriverId(), s.getPeriodStart(), s.getPeriodEnd(), s.getAmount(), s.getStatus(),
                s.getCreatedAt(), s.getUpdatedAt());
    }
}
