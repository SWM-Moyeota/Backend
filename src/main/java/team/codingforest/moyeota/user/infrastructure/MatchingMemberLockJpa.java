package team.codingforest.moyeota.user.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.api.MatchingMemberLock;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

@Component
@RequiredArgsConstructor
public class MatchingMemberLockJpa implements MatchingMemberLock {
    private final UserRepository users;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(Long memberId) {
        users.findByIdForMatching(memberId).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }
}
