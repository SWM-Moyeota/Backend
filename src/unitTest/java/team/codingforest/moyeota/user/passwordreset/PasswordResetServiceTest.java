package team.codingforest.moyeota.user.passwordreset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.passwordreset.application.*;
import team.codingforest.moyeota.user.passwordreset.domain.*;
import team.codingforest.moyeota.user.passwordreset.dto.*;
import team.codingforest.moyeota.user.passwordreset.infrastructure.PasswordResetSecrets;
import team.codingforest.moyeota.user.common.crypto.FieldHasher;
import team.codingforest.moyeota.user.common.exception.*;
import team.codingforest.moyeota.user.local.domain.PasswordHasher;
import team.codingforest.moyeota.user.local.domain.PasswordPolicy;
import team.codingforest.moyeota.user.auth.domain.RefreshTokens;
import team.codingforest.moyeota.user.verification.domain.SmsSender;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PasswordResetServiceTest {
    final ResetAccounts accounts = mock(ResetAccounts.class);
    final ResetGrants grants = mock(ResetGrants.class);
    final PasswordHasher hasher = mock(PasswordHasher.class);
    final RefreshTokens refresh = mock(RefreshTokens.class);
    final ResetRequests requests = mock(ResetRequests.class);
    final SmsSender sender = mock(SmsSender.class);
    final PasswordResetSecrets secrets = new PasswordResetSecrets(new FieldHasher("test-only-key"));
    final Instant now = Instant.parse("2026-11-09T00:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    final String token = secrets.newOpaqueValue();
    final String digest = secrets.tokenDigest(token);
    final ResetAccount account = new ResetAccount(1L,"01012345678","phone-hash","old-hash",0);
    final PasswordResetConfirmService confirm = new PasswordResetConfirmService(accounts,grants,secrets,hasher,refresh,clock);
    final PasswordResetGrantIssuer issuer = new PasswordResetGrantIssuer(accounts,grants,secrets,clock);
    ResetGrant grant;

    @BeforeEach void setUp() {
        grant = new ResetGrant(UUID.randomUUID(),digest,1L,"phone-hash",0,now.plusSeconds(600),null,now);
        when(grants.findOwnerId(digest)).thenReturn(Optional.of(1L));
        when(accounts.lockEligible(1L)).thenReturn(Optional.of(account));
        when(grants.lockByTokenHash(digest)).thenReturn(Optional.of(grant));
        when(hasher.hash("NewPassw0rd!")).thenReturn("new-hash");
    }
    PasswordResetConfirmRequest input() { return new PasswordResetConfirmRequest(token,"NewPassw0rd!","NewPassw0rd!"); }

    @Test void 성공하면_대상회원의_비밀번호와_모든권한을_함께_변경한다() {
        confirm.confirm(input());
        var order = inOrder(accounts, grants, refresh);
        order.verify(accounts).lockEligible(1L);
        order.verify(grants).lockByTokenHash(digest);
        order.verify(accounts).changePassword(1L,"new-hash",now);
        order.verify(grants).consumeAll(1L,now);
        order.verify(refresh).cancelAllByUserId(1L,now);
    }
    @Test void 확인비밀번호가_다르면_토큰도_소비하지_않는다() {
        assertThatThrownBy(() -> confirm.confirm(new PasswordResetConfirmRequest(token,"NewPassw0rd!","Different1!")))
                .extracting("errorCode").isEqualTo(UserErrorCode.PASSWORD_CONFIRM_MISMATCH);
        verify(grants,never()).consumeAll(any(),any());
        verify(accounts,never()).lockEligible(any());
    }
    @Test void 만료시각과_같으면_변경할수없다() {
        when(grants.lockByTokenHash(digest)).thenReturn(Optional.of(new ResetGrant(grant.id(),digest,1L,"phone-hash",0,now,null,now)));
        assertInvalid();
    }
    @Test void 이미_사용한_허가증은_거절한다() {
        when(grants.lockByTokenHash(digest)).thenReturn(Optional.of(new ResetGrant(grant.id(),digest,1L,"phone-hash",0,now.plusSeconds(10),now,now)));
        assertInvalid();
    }
    @Test void 이전세대의_허가증은_거절한다() {
        when(accounts.lockEligible(1L)).thenReturn(Optional.of(new ResetAccount(1L,"01012345678","phone-hash","old-hash",1)));
        assertInvalid();
    }
    @Test void 번호가_변경됐으면_거절한다() {
        when(accounts.lockEligible(1L)).thenReturn(Optional.of(new ResetAccount(1L,"01011112222","changed","old-hash",0)));
        assertInvalid();
    }
    @Test void 탈퇴했으면_거절한다() { when(accounts.lockEligible(1L)).thenReturn(Optional.empty()); assertInvalid(); }
    @Test void 기존과_같은_비밀번호는_권한을_소비하지_않는다() {
        when(hasher.matches("NewPassw0rd!","old-hash")).thenReturn(true);
        assertThatThrownBy(() -> confirm.confirm(input())).extracting("errorCode").isEqualTo(UserErrorCode.SAME_PASSWORD);
        verify(grants,never()).consumeAll(any(),any());
    }
    @Test void 문자코드_확인후에도_계정세대를_다시_검사한다() {
        assertThatThrownBy(() -> issuer.issue(new VerifiedResetProof(1L,"phone-hash",99)))
                .extracting("errorCode").isEqualTo(UserErrorCode.PASSWORD_RESET_VERIFICATION_FAILED);
        verify(grants,never()).save(any());
    }
    @Test void 허가증은_원문대신_검증값만_저장한다() {
        var response = issuer.issue(new VerifiedResetProof(1L,"phone-hash",0));
        var captor = org.mockito.ArgumentCaptor.forClass(ResetGrant.class);
        verify(grants).save(captor.capture());
        assertThat(captor.getValue().tokenHash()).isEqualTo(secrets.tokenDigest(response.resetToken())).isNotEqualTo(response.resetToken());
        assertThat(captor.getValue().expiresAt()).isEqualTo(now.plusSeconds(600));
    }
    @Test void 계정이_다르면_문자발송을_하지않는다() {
        when(accounts.findEligible("hong123","phone-hash")).thenReturn(Optional.empty());
        new PasswordResetSmsWorker(requests,accounts,secrets,sender).handle(new ResetRequestJob("request","hong123","phone-hash",now.plusSeconds(180)));
        verifyNoInteractions(sender);
        verify(requests).invalidate("request");
    }
    @Test void 발송실패는_코드를_무효화한다() {
        when(accounts.findEligible("hong123","phone-hash")).thenReturn(Optional.of(account));
        when(requests.prepareCode(eq("request"),any(),anyString())).thenReturn(true);
        doThrow(new IllegalStateException("테스트 장애")).when(sender).send(anyString(),anyString());
        assertThatThrownBy(() -> new PasswordResetSmsWorker(requests,accounts,secrets,sender)
                .handle(new ResetRequestJob("request","hong123","phone-hash",now.plusSeconds(180)))).isInstanceOf(IllegalStateException.class);
        verify(requests).invalidate("request");
        verify(requests,never()).markSent(any());
    }
    @Test void 인증용도와_요청마다_코드검증값이_달라진다() {
        assertThat(secrets.codeDigest("a","123456")).isNotEqualTo(secrets.codeDigest("b","123456"))
                .isNotEqualTo(secrets.tokenDigest("123456"));
        assertThat(secrets.newOpaqueValue()).matches("[A-Za-z0-9_-]{43}");
    }
    @Test void 비밀번호_문자수와_UTF8바이트를_함께검사한다() {
        PasswordPolicy.validate("Ab1!" + "가".repeat(22));
        assertThatThrownBy(() -> PasswordPolicy.validate("Ab1!"+"가".repeat(23)))
                .extracting("errorCode").isEqualTo(UserErrorCode.PASSWORD_POLICY_VIOLATION);
    }
    private void assertInvalid() {
        assertThatThrownBy(() -> confirm.confirm(input())).extracting("errorCode").isEqualTo(UserErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        verify(accounts,never()).changePassword(any(),any(),any());
        verifyNoInteractions(refresh);
    }
}
