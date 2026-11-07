package team.codingforest.moyeota.matching.party.domain;

import java.util.List;

/**
 *  방 알림(약속 시각 알림·출발 재촉) 차례를 처리한 결과
 *   - handled=false                  : 아직 차례가 아니거나 이미 처리했거나 닫힌 방이다. 아무것도 바뀌지 않았다
 *   - handled=true, recipientIds 없음 : 차례는 처리했지만 보내지 않는다(늦었거나 받을 사람이 없다). 처리한 것은 저장해야 한다
 *   - handled=true, recipientIds 있음 : 차례를 처리했고 이 멤버들에게 보낸다
 */
public record PartyNotice(boolean handled, List<Long> recipientIds) {

    public PartyNotice {
        recipientIds = List.copyOf(recipientIds);
    }

    static PartyNotice notDue() {
        return new PartyNotice(false, List.of());
    }

    static PartyNotice silent() {
        return new PartyNotice(true, List.of());
    }

    static PartyNotice send(List<Long> recipientIds) {
        return new PartyNotice(true, recipientIds);
    }

    public boolean shouldSend() {
        return handled && !recipientIds.isEmpty();
    }
}
