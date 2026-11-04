package team.codingforest.moyeota.user.deletion.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.user.api.OngoingPartyChecker;
import team.codingforest.moyeota.user.api.UserDeletedEvent;
import team.codingforest.moyeota.user.auth.domain.PrincipalCache;
import team.codingforest.moyeota.user.auth.domain.RefreshTokens;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.deletion.domain.UserDeletions;
import team.codingforest.moyeota.user.local.domain.LocalUser;
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.local.domain.PasswordHasher;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeletionService {
    private final Users users;
    private final LocalUsers localUsers;
    private final UserProfiles userProfiles;
    private final UserDeletions userDeletions;
    private final RefreshTokens refreshTokens;
    private final PrincipalCache principalCache;
    private final PasswordHasher passwordHasher;
    private final OngoingPartyChecker ongoingPartyChecker;
    private final ApplicationEventPublisher eventPublisher;
    private final AfterCommitExecutor afterCommitExecutor;

    @Transactional
    public void delete(Long userId, String rawPassword) {
        // 비밀번호가 없는 계정(소셜)은 아직 탈퇴 경로가 없다 - 소셜 로그인이 붙을 때 본인 확인 수단과 함께 연다
        LocalUser localUser = localUsers.findByUserId(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.PASSWORD_MISMATCH));
        if (!passwordHasher.matches(rawPassword, localUser.getPassword())) {
            throw new UserException(UserErrorCode.PASSWORD_MISMATCH);
        }
        if (ongoingPartyChecker.hasOngoingParty(userId)) {
            throw new UserException(UserErrorCode.ONGOING_PARTY_EXISTS);
        }

        Instant now = Instant.now();
        User user = users.findById(userId);
        user.delete(now);
        users.save(user);

        // 프로필을 지우면 번호를 알 수 없어서 지우기 전에 해시를 남긴다
        userProfiles.findByUserId(userId)
                .ifPresent(profile -> userDeletions.record(profile.getPhoneNumber(), now));
        userProfiles.deleteByUserId(userId);
        localUsers.deleteByUserId(userId);
        refreshTokens.cancelAllByUserId(userId, now);

        eventPublisher.publishEvent(new UserDeletedEvent(userId, user.getPublicId()));
        // 커밋 전에 비우면 그 사이 요청이 탈퇴 전 상태를 다시 캐시에 올린다
        afterCommitExecutor.execute("user.principal-evict", () -> principalCache.evict(user.getPublicId()));

        log.info("회원 탈퇴 userId={}", userId);
    }
}
