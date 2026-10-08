package team.codingforest.moyeota.payment.dto;

import java.util.List;

public record RideCompletedCommand(Long partyId, Long driverId, int fare, String departure, String destination, List<Long> memberIds) {
}
