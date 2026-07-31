package team.codingforest.moyeota.common;

import java.time.Instant;

public interface TimeTrackable {
    Instant getCreatedAt();
    Instant getUpdatedAt();
}
