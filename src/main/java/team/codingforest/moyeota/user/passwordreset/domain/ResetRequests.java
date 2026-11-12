package team.codingforest.moyeota.user.passwordreset.domain;

import java.util.Optional;

public interface ResetRequests {
 void accept(ResetRequestJob job);
 Optional<ResetRequestJob> claimNext();
 boolean prepareCode(String id, VerifiedResetProof proof, String digest);
 void markSent(String id);
 void invalidate(String id);
 VerifiedResetProof verifyAndConsume(String id, String digest);
 void checkClient(String clientKey);
}
