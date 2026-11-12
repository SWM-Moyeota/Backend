package team.codingforest.moyeota.user.passwordreset.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.Optional;
import team.codingforest.moyeota.user.passwordreset.domain.*;
import team.codingforest.moyeota.user.local.infrastructure.*;
import team.codingforest.moyeota.user.users.infrastructure.UserRepository;
import team.codingforest.moyeota.user.profile.infrastructure.UserProfileRepository;

@Repository
@RequiredArgsConstructor
public class ResetAccountsJpa implements ResetAccounts {
 private final LocalUserRepository local;
 private final UserRepository users;
 private final UserProfileRepository profiles;

 @Override @Transactional(readOnly = true)
 public Optional<ResetAccount> findEligible(String loginId, String phoneHash) {
   return local.findByLoginId(loginId).flatMap(this::eligible)
     .filter(account -> account.phoneHash().equals(phoneHash));
 }

 @Override @Transactional(propagation = Propagation.MANDATORY)
 public Optional<ResetAccount> lockEligible(Long id) {
   return local.findByIdForUpdate(id).flatMap(this::eligible);
 }

 private Optional<ResetAccount> eligible(LocalUserEntity member) {
   if (users.findById(member.getId()).filter(u -> u.getDeletedAt() == null).isEmpty()) return Optional.empty();
   return profiles.findById(member.getId()).map(p -> new ResetAccount(member.getId(),
     p.getPhoneNumber(), p.getPhoneHash(), member.getPassword(), member.getPasswordResetGeneration()));
 }

 @Override @Transactional(propagation = Propagation.MANDATORY)
 public void changePassword(Long id, String hash, Instant now) {
   local.findByIdForUpdate(id).orElseThrow().resetPassword(hash, now);
 }
}
