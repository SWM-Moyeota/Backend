package team.codingforest.moyeota.faresplit.domain;

import java.util.List;

/**
 *  정산 알림(송금 재요청·입금 확인 요청) 차례를 처리한 결과
 *   - handled=false                  : 아직 차례가 아니거나 닫힌 정산이다. 아무것도 바뀌지 않았다
 *   - handled=true, recipientIds 없음 : 차례는 넘겼지만 보내지 않는다(방해 금지 시간, 받을 사람 없음). 넘긴 것은 저장해야 한다
 *   - handled=true, recipientIds 있음 : 차례를 넘겼고 이 사람들에게 보낸다
 */
public record FareSplitNotice(boolean handled, List<Long> recipientIds) {

    public FareSplitNotice {
        recipientIds = List.copyOf(recipientIds);
    }

    static FareSplitNotice notDue() {
        return new FareSplitNotice(false, List.of());
    }

    static FareSplitNotice silent() {
        return new FareSplitNotice(true, List.of());
    }

    static FareSplitNotice send(List<Long> recipientIds) {
        return new FareSplitNotice(true, recipientIds);
    }

    public boolean shouldSend() {
        return handled && !recipientIds.isEmpty();
    }
}
