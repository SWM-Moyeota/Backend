package team.codingforest.moyeota.matching.party.domain;

public interface PartyChangeNotifier {
    void changed(Long partyId);
    void closed(Long partyId);

    /** 한 사람이 방을 나갔다 - 그 사람이 붙여 둔 연결을 정리한다. 남은 사람에게 알리는 것은 changed 가 한다 */
    void left(Long partyId, Long memberId);
}
