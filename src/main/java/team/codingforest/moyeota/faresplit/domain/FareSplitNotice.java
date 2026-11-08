package team.codingforest.moyeota.faresplit.domain;

import java.util.List;

/**
 *  정산 알림(송금 재요청·입금 확인 요청)을 받을 사람. 비어 있으면 아직 때가 아니거나 닫힌 정산이라 아무것도 바뀌지 않았다
 */
public record FareSplitNotice(List<Long> recipientIds) {

    public FareSplitNotice {
        recipientIds = List.copyOf(recipientIds);
    }

    static FareSplitNotice notDue() {
        return new FareSplitNotice(List.of());
    }

    static FareSplitNotice send(List<Long> recipientIds) {
        return new FareSplitNotice(recipientIds);
    }

    public boolean shouldSend() {
        return !recipientIds.isEmpty();
    }
}
