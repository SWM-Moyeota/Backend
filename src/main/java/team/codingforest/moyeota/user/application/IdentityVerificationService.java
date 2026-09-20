package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.user.api.IdentityAccess;
import team.codingforest.moyeota.user.domain.*;

import java.time.Clock;
import java.time.Instant;

import static team.codingforest.moyeota.user.domain.exception.IdentityErrorCode.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityVerificationService implements IdentityAccess {
    private final IdentityVerifications verifications;
    private final IdentityProvider provider;
    private final IdentityHasher hasher;
    private final IdentitySettings settings;
    private final Clock identityClock;

    public IdentityRequest start(Long userId) {
        settings.requireConfigured();
        IdentityRequest request = verifications.start(userId, settings.storeId(), settings.channelKey(),
                identityClock.instant(), settings.validity());
        log.info("본인인증 요청 준비 requestId={}", request.id());
        return request;
    }

    /** 외부 호출 중 DB 트랜잭션/잠금을 유지하지 않는다. 저장은 별도 빈에서 짧게 처리한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Status complete(Long userId, String requestId) {
        IdentityRequest request = verifications.findOwned(requestId, userId);
        var existing = verifications.findVerified(userId);
        if (existing.isPresent()) return completedForRequest(existing.get(), requestId);
        settings.requireConfigured();
        try {
            IdentityProvider.Result result = provider.lookup(request);
            Instant now = identityClock.instant();
            validate(request, result, now);
            String diHash = hasher.hash(result.di());
            VerifiedIdentity identity;
            try {
                identity = verifications.complete(request, diHash, result.verifiedAt(), now);
            } catch (DataIntegrityViolationException e) {
                // 다른 계정의 동시 인증은 DB unique가 최종 방어한다. 롤백이 끝난 뒤 분류한다.
                if (verifications.existsByDiHash(diHash)) throw new BusinessException(IDENTITY_IN_USE);
                throw e;
            }
            log.info("본인인증 완료 requestId={}", requestId);
            return Status.from(identity);
        } catch (BusinessException e) {
            log.warn("본인인증 처리 보류 requestId={} code={}", requestId, e.getErrorCode().getCode());
            throw e;
        }
    }

    private void validate(IdentityRequest request, IdentityProvider.Result result, Instant now) {
        if (result == null || !request.id().equals(result.id())) throw new BusinessException(INVALID_RESULT);
        if (!"VERIFIED".equals(result.status())) throw new BusinessException(NOT_VERIFIED);
        if (!"V2".equals(result.version()) || !"LIVE".equals(result.channelType())
                || !request.channelKey().equals(result.channelKey())
                || result.verifiedAt() == null || result.di() == null || result.di().isBlank()) {
            throw new BusinessException(INVALID_RESULT);
        }
        if (result.verifiedAt().isBefore(request.createdAt()) || result.verifiedAt().isAfter(now)) {
            throw new BusinessException(INVALID_RESULT);
        }
        // 서버 재시도 시각이 아니라 외부 인증 완료 시각으로 만료를 판정한다.
        if (!result.verifiedAt().isBefore(request.expiresAt())) throw new BusinessException(EXPIRED);
    }

    private Status completedForRequest(VerifiedIdentity identity, String requestId) {
        if (!identity.requestId().equals(requestId)) throw new BusinessException(ALREADY_VERIFIED);
        return Status.from(identity);
    }

    public Status status(Long userId) {
        return verifications.findVerified(userId).map(Status::from).orElse(new Status(false, null));
    }

    @Override
    public void requireVerified(Long userId) {
        if (verifications.findVerified(userId).isEmpty()) throw new BusinessException(REQUIRED);
    }

    public record Status(boolean verified, Instant verifiedAt) {
        static Status from(VerifiedIdentity identity) { return new Status(true, identity.verifiedAt()); }
    }
}
