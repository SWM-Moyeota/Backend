package team.codingforest.moyeota.user.social.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.user.common.exception.*;
import team.codingforest.moyeota.user.users.domain.*;
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.verification.application.SmsVerificationService;
import team.codingforest.moyeota.user.social.domain.*;
import team.codingforest.moyeota.user.social.infrastructure.*;
import team.codingforest.moyeota.user.social.apple.AppleCredentialStore;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoginMethodsService {
    private final Users users;
    private final team.codingforest.moyeota.common.crypto.FieldHasher phoneHasher;
    private final LocalUsers localUsers;
    private final UserProfiles profiles;
    private final SocialUserRepository social;
    private final SmsVerificationService sms;
    private final SocialProofStore proofs;
    private final AppleCredentialStore credentials;

    public Methods list(Long userId) {
        return new Methods(localUsers.findByUserId(userId).isPresent(), social.findAllByUserId(userId).stream()
                .filter(s -> s.getSocialType().name().equals("GOOGLE") || s.getSocialType().name().equals("APPLE"))
                .map(s -> new Method(s.getSocialType().name(), s.getStatus())).toList());
    }
    public void send(Long userId) { sms.sendLinkCode(phone(userId)); }
    public Verification verify(Long userId, String code) {
        String phone = phone(userId);
        sms.checkLinkCode(phone, code);
        return new Verification(proofs.issue("LINK_SMS", userId, phoneHasher.hash(phone)), 300);
    }
    private String phone(Long id) {
        return profiles.findByUserId(id).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)).getPhoneNumber();
    }
    @Transactional
    public void link(Long userId, SocialProfile profile, String verification, UUID credentialId) {
        if (users.findByIdForUpdate(userId).isDeleted()) throw new UserException(UserErrorCode.USER_NOT_FOUND);
        String verifiedPhone = proofs.consume(verification, "LINK_SMS", userId, UserErrorCode.SOCIAL_LINK_VERIFICATION_EXPIRED);
        if (!verifiedPhone.equals(phoneHasher.hash(phone(userId)))) throw new UserException(UserErrorCode.SOCIAL_LINK_VERIFICATION_EXPIRED);
        var owner = social.findBySocialTypeAndSocialId(profile.socialType(), profile.socialId());
        if (owner.isPresent() && !owner.get().getUserId().equals(userId)) throw conflict();
        var existing = social.findAllByUserId(userId).stream().filter(s -> s.getSocialType() == profile.socialType()).findFirst();
        if (existing.isPresent() && !existing.get().getSocialId().equals(profile.socialId())) throw conflict();
        SocialUserEntity entity = existing.orElseGet(() -> SocialUserEntity.from(userId, profile.socialType(), profile.socialId()));
        entity.activate();
        try { social.saveAndFlush(entity); } catch (DataIntegrityViolationException e) { throw conflict(); }
        if (credentialId != null) credentials.attach(credentialId, userId);
    }
    private UserException conflict() { return new UserException(UserErrorCode.SOCIAL_LINK_CONFLICT); }
    public record Method(String provider, String status) {}
    public record Methods(boolean hasPassword, List<Method> methods) {}
    public record Verification(String verificationToken, long expiresInSeconds) {}
}
