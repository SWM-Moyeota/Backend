package team.codingforest.moyeota.matching.application;

import team.codingforest.moyeota.matching.domain.Party;

public interface PartyCompletionPolicy {
    void onCompleted(Party party);
}
