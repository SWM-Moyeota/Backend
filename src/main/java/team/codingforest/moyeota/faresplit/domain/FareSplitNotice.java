package team.codingforest.moyeota.faresplit.domain;

import java.util.List;

/**
 *  스케줄러가 보내는 정산 알림(송금 재요청)을 받을 사람과 그 사람이 보낼 금액
 *
 *  @param payments 받을 사람마다 보낼 금액. 비어 있으면 보낼 알림이 없다
 */
public record FareSplitNotice(List<FarePayment> payments) {
    public FareSplitNotice {
        payments = List.copyOf(payments);
    }

    static FareSplitNotice notDue() {
        return new FareSplitNotice(List.of());
    }

    static FareSplitNotice send(List<FarePayment> payments) {
        return new FareSplitNotice(payments);
    }

    public boolean shouldSend() {
        return !payments.isEmpty();
    }

    public List<Long> recipientIds() {
        return payments.stream().map(FarePayment::memberId).toList();
    }
}
