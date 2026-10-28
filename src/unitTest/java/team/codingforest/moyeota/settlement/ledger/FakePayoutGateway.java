package team.codingforest.moyeota.settlement.ledger;

import team.codingforest.moyeota.settlement.ledger.domain.PayoutGateway;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutOrder;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class FakePayoutGateway implements PayoutGateway {
    public static final String DEFAULT_PG_ID_PREFIX = "pg-";

    private final Deque<Function<PayoutOrder, PayoutResult>> responses = new ArrayDeque<>();
    private final Map<String, PayoutResult> records = new HashMap<>();
    private final Map<String, RuntimeException> findFailures = new HashMap<>();
    private final List<PayoutOrder> requests = new ArrayList<>();
    private final List<String> findCalls = new ArrayList<>();

    public FakePayoutGateway willReturn(PayoutResult... results) {
        for(PayoutResult result : results) {
            responses.addLast(order -> result);
        }
        return this;
    }

    public FakePayoutGateway willThrow(RuntimeException exception) {
        responses.addLast(order -> {
            throw exception;
        });
        return this;
    }

    public FakePayoutGateway willFind(String payoutKey, PayoutResult result) {
        records.put(payoutKey, result);
        return this;
    }

    public FakePayoutGateway willThrowOnFind(String payoutKey, RuntimeException exception) {
        findFailures.put(payoutKey, exception);
        return this;
    }

    @Override
    public PayoutResult request(PayoutOrder order) {
        requests.add(order);

        Function<PayoutOrder, PayoutResult> response = responses.pollFirst();
        if(response == null) return new PayoutResult.Succeeded(DEFAULT_PG_ID_PREFIX + order.payoutId());

        return response.apply(order);
    }

    @Override
    public Optional<PayoutResult> find(String payoutKey) {
        findCalls.add(payoutKey);

        RuntimeException failure = findFailures.get(payoutKey);
        if(failure != null) throw failure;

        return Optional.ofNullable(records.get(payoutKey));
    }

    public List<PayoutOrder> requests() {
        return List.copyOf(requests);
    }

    public int requestCount() {
        return requests.size();
    }

    public List<String> findCalls() {
        return List.copyOf(findCalls);
    }
}
