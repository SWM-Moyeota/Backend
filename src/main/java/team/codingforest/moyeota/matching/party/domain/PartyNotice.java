package team.codingforest.moyeota.matching.party.domain;

import java.util.List;

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
