package team.codingforest.moyeota.user.passwordreset.domain;

import java.util.Optional;
import java.time.Instant;

public interface ResetAccounts {
 Optional<ResetAccount> findEligible(String loginId, String phoneHash);
 Optional<ResetAccount> lockEligible(Long userId);
 void changePassword(Long userId, String hash, Instant now);
}
