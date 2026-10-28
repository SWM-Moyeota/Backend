package team.codingforest.moyeota.settlement.ledger;

import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedgers;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public class FakeSettlementLedgers implements SettlementLedgers {
    private final Map<Long, SettlementLedger> store = new TreeMap<>();
    private final FakeSettlements settlements;
    private long sequence = 0;

    public FakeSettlementLedgers(FakeSettlements settlements) {
        this.settlements = settlements;
    }

    @Override
    public SettlementLedger save(SettlementLedger ledger) {
        if(ledger.getId() != null) {
            throw new IllegalStateException("정산 원장은 고칠 수 없습니다. ledgerId=" + ledger.getId());
        }
        if(existsByMatchId(ledger.getMatchId())) {
            throw new DataIntegrityViolationException("uk_settlement_ledger_match: matchId=" + ledger.getMatchId());
        }

        Long id = ++sequence;
        SettlementLedger saved = withSettlement(ledger, id, ledger.getSettlementId(), Instant.now().truncatedTo(ChronoUnit.MICROS));
        store.put(id, saved);
        return saved;
    }

    @Override
    public boolean existsByMatchId(Long matchId) {
        return store.values().stream().anyMatch(l -> l.getMatchId().equals(matchId));
    }

    @Override
    public List<SettlementLedger> findBySettlementId(Long settlementId) {
        return store.values().stream()
                .filter(l -> Objects.equals(l.getSettlementId(), settlementId))
                .sorted(Comparator.comparing(SettlementLedger::getOccurredAt).thenComparing(SettlementLedger::getId))
                .toList();
    }

    @Override
    public List<SettlementLedger> findUnsettledByDriverIn(Long taxiDriverId, SettlementPeriod week) {
        return findUnsettledByDriver(taxiDriverId).stream()
                .filter(l -> week.contains(l.getOccurredAt()))
                .toList();
    }

    @Override
    public List<Long> findDriverIdsToSettle(SettlementPeriod week) {
        return store.values().stream()
                .filter(l -> l.getSettlementId() == null && l.getOccurredAt().isBefore(week.end()))
                .map(SettlementLedger::getTaxiDriverId)
                .distinct()
                .filter(driverId -> settlements.findByDriverAndPeriod(driverId, week).isEmpty())
                .sorted()
                .toList();
    }

    @Override
    public int assignToSettlement(Long taxiDriverId, Instant cutoff, Long settlementId) {
        List<SettlementLedger> targets = store.values().stream()
                .filter(l -> l.getTaxiDriverId().equals(taxiDriverId) && l.getSettlementId() == null && l.getOccurredAt().isBefore(cutoff))
                .toList();

        for(SettlementLedger target : targets) {
            store.put(target.getId(), withSettlement(target, target.getId(), settlementId, target.getCreatedAt()));
        }
        return targets.size();
    }

    @Override
    public int sumSettlementAmount(Long settlementId) {
        long sum = store.values().stream()
                .filter(l -> Objects.equals(l.getSettlementId(), settlementId))
                .mapToLong(SettlementLedger::getSettlementAmount)
                .sum();
        return Math.toIntExact(sum);
    }

    public List<SettlementLedger> findUnsettledByDriver(Long taxiDriverId) {
        return store.values().stream()
                .filter(l -> l.getTaxiDriverId().equals(taxiDriverId) && l.getSettlementId() == null)
                .sorted(Comparator.comparing(SettlementLedger::getOccurredAt).thenComparing(SettlementLedger::getId))
                .toList();
    }

    public Optional<SettlementLedger> findByMatchId(Long matchId) {
        return store.values().stream()
                .filter(l -> l.getMatchId().equals(matchId))
                .findFirst();
    }

    public int count() {
        return store.size();
    }

    private static SettlementLedger withSettlement(SettlementLedger l, Long id, Long settlementId, Instant createdAt) {
        return SettlementLedger.load(id, l.getTaxiDriverId(), settlementId, l.getMatchId(), l.getSettlementAmount(), l.getTotalFare(),
                l.getPlatformPromotion(), l.getOccurredAt(), createdAt);
    }
}
