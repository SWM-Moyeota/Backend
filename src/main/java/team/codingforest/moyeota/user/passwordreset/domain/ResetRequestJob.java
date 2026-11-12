package team.codingforest.moyeota.user.passwordreset.domain;

import java.time.Instant;
import java.util.UUID;

public record ResetRequestJob(String requestId, String loginId, String phoneHash, Instant expiresAt) {

@Override public String toString() { return "ResetRequestJob[보안 정보 생략]"; }
}
