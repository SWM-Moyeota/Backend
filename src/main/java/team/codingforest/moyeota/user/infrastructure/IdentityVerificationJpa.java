package team.codingforest.moyeota.user.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.user.domain.*;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static team.codingforest.moyeota.user.domain.exception.IdentityErrorCode.*;

@Repository
@RequiredArgsConstructor
public class IdentityVerificationJpa implements IdentityVerifications {
    private final UserRepository users;
    private final IdentityRequestRepository requests;
    private final VerifiedIdentityRepository identities;

    @Override
    @Transactional
    public IdentityRequest start(Long userId, String storeId, String channelKey, Instant now, Duration validity) {
        lockUser(userId);
        if (identities.existsById(userId)) throw new BusinessException(ALREADY_VERIFIED);
        var latest = requests.findFirstByUserIdOrderByCreatedAtDesc(userId).map(IdentityRequestEntity::toDomain);
        if (latest.isPresent() && now.isBefore(latest.get().expiresAt())) return latest.get();
        // DB timestamp(6)와 동일한 정밀도로 저장/응답한다.
        Instant createdAt = now.truncatedTo(ChronoUnit.MICROS);
        IdentityRequest request = new IdentityRequest("iv-" + UUID.randomUUID(), userId, storeId, channelKey,
                createdAt, createdAt.plus(validity));
        return requests.save(new IdentityRequestEntity(request)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public IdentityRequest findOwned(String requestId, Long userId) {
        // 타인 요청과 존재하지 않는 요청을 동일하게 응답한다.
        return requests.findByIdAndUserId(requestId, userId).map(IdentityRequestEntity::toDomain)
                .orElseThrow(() -> new BusinessException(REQUEST_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerifiedIdentity> findVerified(Long userId) {
        return identities.findById(userId).map(VerifiedIdentityEntity::toDomain);
    }

    @Override
    @Transactional
    public VerifiedIdentity complete(IdentityRequest request, String diHash, Instant verifiedAt, Instant now) {
        lockUser(request.userId());
        findOwned(request.id(), request.userId());
        var existing = findVerified(request.userId());
        if (existing.isPresent()) {
            if (!existing.get().requestId().equals(request.id())) throw new BusinessException(ALREADY_VERIFIED);
            return existing.get();
        }
        if (identities.existsByDiHash(diHash)) throw new BusinessException(IDENTITY_IN_USE);
        VerifiedIdentity identity = new VerifiedIdentity(request.userId(), request.id(),
                verifiedAt.truncatedTo(ChronoUnit.MICROS), now.truncatedTo(ChronoUnit.MICROS));
        return identities.saveAndFlush(new VerifiedIdentityEntity(identity, diHash)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByDiHash(String diHash) { return identities.existsByDiHash(diHash); }

    private void lockUser(Long userId) {
        users.findByIdForIdentityUpdate(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
