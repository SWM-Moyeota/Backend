package team.codingforest.moyeota.matching.api;

import java.util.List;

public record PartyChatSummary(Long id, List<Long> users, String departure, String destination) {
}
