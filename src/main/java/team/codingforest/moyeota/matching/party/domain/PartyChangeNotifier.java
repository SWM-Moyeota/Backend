package team.codingforest.moyeota.matching.party.domain;

public interface PartyChangeNotifier {
    void changed(Long partyId);
    void closed(Long partyId);
    void left(Long partyId, Long memberId);
}
