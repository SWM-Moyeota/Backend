package team.codingforest.moyeota.user.verification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.user.common.crypto.FieldHasher;
import team.codingforest.moyeota.user.common.domain.PhoneNumber;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.verification.domain.SmsSender;
import team.codingforest.moyeota.user.verification.domain.SmsVerifications;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsVerificationService {
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final Duration DAILY_WINDOW = Duration.ofDays(1);
    private static final int DAILY_LIMIT = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsVerifications smsVerifications;
    private final SmsSender smsSender;
    private final UserProfiles userProfiles;
    private final FieldHasher fieldHasher;

    public void send(String rawPhoneNumber) {
        String phone = new PhoneNumber(rawPhoneNumber).value();
        if (userProfiles.existsByPhoneNumber(phone)) {   // 가입된 번호면 문자 비용을 쓰기 전에 거절
            throw new UserException(UserErrorCode.PHONE_NUMBER_DUPLICATED);
        }

        String phoneHash = fieldHasher.hash(phone);
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        try {
            if (!smsVerifications.tryStartCooldown(phoneHash, COOLDOWN)) {
                throw new UserException(UserErrorCode.SMS_COOLDOWN);
            }
            if (smsVerifications.increaseDailyCount(phoneHash, DAILY_WINDOW) > DAILY_LIMIT) {
                throw new UserException(UserErrorCode.SMS_DAILY_LIMIT_EXCEEDED);
            }
            smsVerifications.saveCode(phoneHash, code, CODE_TTL);
        } catch (DataAccessException e) {
            throw unavailable(e);
        }

        try {
            smsSender.send(phone, "[모여타] 인증번호 [%s]를 입력해 주세요.".formatted(code));
        } catch (UserException e) {
            releaseCooldown(phoneHash);
            throw e;
        }
    }

    public String verify(String rawPhoneNumber, String code) {
        String phoneHash = fieldHasher.hash(new PhoneNumber(rawPhoneNumber).value());
        try {
            String saved = smsVerifications.findCode(phoneHash)
                    .orElseThrow(() -> new UserException(UserErrorCode.SMS_CODE_EXPIRED));

            // 비교 전에 먼저 센다 - 동시에 여러 번 보내도 MAX_ATTEMPTS 번까지만 비교까지 감
            long attempts = smsVerifications.increaseAttempts(phoneHash, CODE_TTL);
            if (attempts > MAX_ATTEMPTS) {
                smsVerifications.deleteCode(phoneHash);
                throw new UserException(UserErrorCode.SMS_TOO_MANY_ATTEMPTS);
            }

            if (!matches(saved, code)) {
                if (attempts == MAX_ATTEMPTS) {
                    smsVerifications.deleteCode(phoneHash);
                    throw new UserException(UserErrorCode.SMS_TOO_MANY_ATTEMPTS);
                }
                throw new UserException(UserErrorCode.SMS_CODE_MISMATCH);
            }

            smsVerifications.deleteCode(phoneHash);
            String token = UUID.randomUUID().toString();
            smsVerifications.saveVerified(token, phoneHash, VERIFIED_TTL);
            return token;
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    // 가입 직전 확인 - 이 번호로 인증한 토큰인지
    public void checkVerified(String verificationToken, String rawPhoneNumber) {
        String phoneHash = fieldHasher.hash(new PhoneNumber(rawPhoneNumber).value());
        try {
            String verified = smsVerifications.findVerifiedPhone(verificationToken)
                    .orElseThrow(() -> new UserException(UserErrorCode.SMS_NOT_VERIFIED));
            if (!verified.equals(phoneHash)) {
                throw new UserException(UserErrorCode.SMS_NOT_VERIFIED);
            }
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    // 가입이 끝나면 토큰을 지움 - 실패해도 10분 뒤 만료되고 같은 번호 재가입은 phone_hash UNIQUE 가 막음
    public void consume(String verificationToken) {
        try {
            smsVerifications.deleteVerified(verificationToken);
        } catch (DataAccessException e) {
            log.warn("인증 토큰 삭제 실패, TTL 만료에 맡김", e);
        }
    }

    // 비교 시간이 틀린 자리에 따라 달라지지 않게 함
    private boolean matches(String saved, String input) {
        return input != null && MessageDigest.isEqual(
                saved.getBytes(StandardCharsets.UTF_8), input.getBytes(StandardCharsets.UTF_8));
    }

    private void releaseCooldown(String phoneHash) {
        try {
            smsVerifications.releaseCooldown(phoneHash);
        } catch (DataAccessException e) {
            log.warn("재전송 대기 해제 실패, TTL 만료에 맡김", e);
        }
    }

    private UserException unavailable(DataAccessException e) {
        log.warn("문자 인증 Redis 실패", e);
        return new UserException(UserErrorCode.SMS_UNAVAILABLE);
    }
}