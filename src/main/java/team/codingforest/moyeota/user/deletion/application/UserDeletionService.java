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
import team.codingforest.moyeota.user.social.domain.SocialUsers;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;

import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeletionService {
    private final Users users;
    private final LocalUsers localUsers;
    private final SocialUsers socialUsers;
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
        confirmIdentity(userId, rawPassword);

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
        // 소셜 연동도 끊는다 - 남겨 두면 같은 소셜 계정이 탈퇴한 회원으로 계속 로그인된다
        socialUsers.deleteByUserId(userId);
        refreshTokens.cancelAllByUserId(userId, now);

        eventPublisher.publishEvent(new UserDeletedEvent(userId, user.getPublicId()));
        // 커밋 전에 비우면 그 사이 요청이 탈퇴 전 상태를 다시 캐시에 올린다
        afterCommitExecutor.execute("user.principal-evict", () -> principalCache.evict(user.getPublicId()));

        log.info("회원 탈퇴 userId={}", userId);
    }

    /**
     * 본인 확인. 로그인 수단 행을 잠그고 본다 - 재발급·비밀번호 재설정과 같은 잠금 순서
     * - 로컬 회원: 현재 비밀번호가 맞아야 한다
     * - 소셜 회원: 비밀번호가 없다. 로그인 토큰으로 요청한 것 자체를 본인 확인으로 본다
     */
    private void confirmIdentity(Long userId, String rawPassword) {
        Optional<LocalUser> localUser = localUsers.findByUserIdForUpdate(userId);
        if (localUser.isPresent()) {
            if (!StringUtils.hasText(rawPassword)) {
                // 검증 애노테이션으로 막던 경우 - 소셜 회원은 비밀번호 없이 보내므로 로컬 회원일 때만 여기서 본다
                throw new IllegalArgumentException("비밀번호는 필수입니다.");
            }
            if (!passwordHasher.matches(rawPassword, localUser.get().getPassword())) {
                throw new UserException(UserErrorCode.PASSWORD_MISMATCH);
            }
            return;
        }

        // 로그인 수단이 하나도 없는 계정 - 본인 확인을 할 수 없어 거절한다
        socialUsers.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.PASSWORD_MISMATCH));
    }
}
