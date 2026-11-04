package team.codingforest.moyeota.settlement.common.toss;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutGateway;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutOrder;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutResult;

import java.util.List;
import java.util.Set;

/**
 * 지급 포트를 토스 지급대행 지급 API 로 구현함. 지급 키를 refPayoutId 와 멱등키로 같이 써서 다시 보내도 지급이 하나만 생기게 함
 */
@RequiredArgsConstructor
public class TossPayoutGateway implements PayoutGateway {
    private static final String PAYOUTS = "/v2/payouts";
    private static final String PAYOUT = "/v2/payouts/{id}";
    private static final String EXPRESS = "EXPRESS";
    private static final String KRW = "KRW";
    private static final String TRANSACTION_DESCRIPTION = "모여타정산";
    // 토스에 지급이 이미 있거나 처리 중일 수 있어 실패로 확정하면 이중 지급될 수 있는 에러
    private static final Set<String> MAYBE_CREATED = Set.of("DUPLICATE_PAYOUT_REQUEST", "PREVIOUS_REQUEST_IN_PROGRESS");

    private final TossPayoutClient client;

    @Override
    public PayoutResult request(PayoutOrder order) {
        TossPayoutRequest body = new TossPayoutRequest(order.payoutKey(), order.sellerId(), EXPRESS,
                new TossPayoutRequest.Amount(KRW, order.amount()), TRANSACTION_DESCRIPTION);

        try {
            TossPayoutList response = client.postEncrypted(PAYOUTS, List.of(body), order.payoutKey(), TossPayoutList.class);
            if(response.items() == null || response.items().isEmpty()) return new PayoutResult.Unknown("토스 응답에 지급이 없음");

            return toResult(response.items().getFirst());
        } catch (TossPayoutException e) {
            return requestFailure(e);
        } catch (RestClientException e) {
            return new PayoutResult.Unknown("토스 응답 없음: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public PayoutResult find(String pgId) {
        try {
            return toResult(client.get(PAYOUT, TossPayout.class, pgId));
        } catch (TossPayoutException e) {
            return new PayoutResult.Unknown("토스 지급 조회 실패: " + reason(e));
        } catch (RestClientException e) {
            return new PayoutResult.Unknown("토스 응답 없음: " + e.getClass().getSimpleName());
        }
    }

    private static PayoutResult toResult(TossPayout payout) {
        if(payout.id() == null || payout.status() == null) return new PayoutResult.Unknown("토스 지급 응답에 id 나 상태가 없음");

        return switch(payout.status()) {
            case "REQUESTED", "IN_PROGRESS" -> new PayoutResult.Accepted(payout.id());
            case "COMPLETED" -> new PayoutResult.Succeeded(payout.id());
            case "FAILED", "REJECTED", "CANCELED", "DELETED" -> new PayoutResult.Failed(failReason(payout));
            default -> new PayoutResult.Unknown("토스 지급 상태를 알 수 없음: " + payout.status());
        };
    }

    /**
     * 토스에 지급이 생기지 않은 게 확실한 4xx 만 실패로 봄. 응답 없음, 5xx, 429, 코드 없는 응답, 중복·처리 중은 결과를 모름
     *
     * @param e 토스 에러
     * @return 실패 또는 모름
     */
    private static PayoutResult requestFailure(TossPayoutException e) {
        HttpStatus status = HttpStatus.resolve(e.getHttpStatus());
        boolean notCreated = status != null && status.is4xxClientError() && status != HttpStatus.TOO_MANY_REQUESTS
                && e.getCode() != null && !MAYBE_CREATED.contains(e.getCode());

        return notCreated ? new PayoutResult.Failed(reason(e)) : new PayoutResult.Unknown(reason(e));
    }

    private static String failReason(TossPayout payout) {
        if(payout.error() == null) return payout.status();

        return payout.error().code() + ": " + payout.error().message();
    }

    private static String reason(TossPayoutException e) {
        return e.getCode() + " (httpStatus=" + e.getHttpStatus() + ", traceId=" + e.getTraceId() + ")";
    }
}
