package team.codingforest.moyeota.user.passwordreset.domain;

import java.time.Instant;
import java.util.UUID;

public record ResetAccount(Long userId, String phoneNumber, String phoneHash, String passwordHash, long generation) {

@Override public String toString() { return "ResetAccount[보안 정보 생략]"; }
}
