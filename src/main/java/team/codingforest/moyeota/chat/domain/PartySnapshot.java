package team.codingforest.moyeota.chat.domain;

import java.util.List;

public record PartySnapshot(
        Long partyId,
        List<Long> userIds,
        String departurePlace,
        String destinationPlace
) {}
