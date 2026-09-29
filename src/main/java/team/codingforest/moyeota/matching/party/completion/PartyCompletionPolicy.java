package team.codingforest.moyeota.matching.party.completion;

import team.codingforest.moyeota.matching.party.domain.Party;

public interface PartyCompletionPolicy {
    void onCompleted(Party party);
}
