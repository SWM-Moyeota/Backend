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
import java.util.function.Function;

/**
 * 요청은 정해 둔 응답을 차례로 돌려주고, 없으면 바로 성공함
 * 조회는 결제사 지급 id 마다 정해 둔 결과를 돌려주고, 없으면 아직 처리 중(Accepted)으로 답함
 */
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

    public FakePayoutGateway willFind(String pgId, PayoutResult result) {
        records.put(pgId, result);
        return this;
    }

    public FakePayoutGateway willThrowOnFind(String pgId, RuntimeException exception) {
        findFailures.put(pgId, exception);
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
    public PayoutResult find(String pgId) {
        findCalls.add(pgId);

        RuntimeException failure = findFailures.get(pgId);
        if(failure != null) throw failure;

        return records.getOrDefault(pgId, new PayoutResult.Accepted(pgId));
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
