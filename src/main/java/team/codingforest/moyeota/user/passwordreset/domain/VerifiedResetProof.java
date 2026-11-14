package team.codingforest.moyeota.user.passwordreset.domain;

import java.time.Instant;
import java.util.UUID;

public record VerifiedResetProof(Long userId, String phoneHash, long generation) {

@Override public String toString() { return "VerifiedResetProof[보안 정보 생략]"; }
}
