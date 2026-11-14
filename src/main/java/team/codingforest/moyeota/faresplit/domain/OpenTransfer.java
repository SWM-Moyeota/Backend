package team.codingforest.moyeota.faresplit.domain;

import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;

import java.time.Instant;

public record OpenTransfer(Long partyId, Long payerId, int payment, Instant sentAt, Instant confirmedAt, Instant createdAt) {
    public TransferStatus status() {
        return TransferStatus.of(sentAt, confirmedAt);
    }
}
