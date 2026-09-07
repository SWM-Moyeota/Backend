package team.codingforest.moyeota.chat.domain;

import java.util.List;

public record PartySnapshot(
        Long partyId,
        List<Long> userIds,
        String departurePlace,
        String destinationPlace
) {
    private static final int MIN_MEMBERS_FOR_CHAT = 2;

    public boolean needsChatRoom() {
        return userIds != null && userIds.size() >= MIN_MEMBERS_FOR_CHAT;
    }
}
