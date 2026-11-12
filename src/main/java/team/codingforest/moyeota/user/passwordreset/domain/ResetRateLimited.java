package team.codingforest.moyeota.user.passwordreset.domain;

import team.codingforest.moyeota.user.common.exception.*;

public class ResetRateLimited extends UserException {
 private final long retryAfter;
 public ResetRateLimited(long retryAfter) {
   super(UserErrorCode.PASSWORD_RESET_RATE_LIMITED);
   this.retryAfter = Math.max(1, retryAfter);
 }
 public long retryAfter() { return retryAfter; }
}
