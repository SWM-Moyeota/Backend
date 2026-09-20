package team.codingforest.moyeota.user.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.user.domain.*;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static team.codingforest.moyeota.user.domain.exception.IdentityErrorCode.*;

class IdentityVerificationServiceTest {
    private final Instant start = Instant.parse("2026-09-20T00:00:00Z");
    private final IdentityRequest request = new IdentityRequest("iv-test", 1L, "store", "channel", start, start.plusSeconds(600));
    private final IdentityVerifications store = mock(IdentityVerifications.class);
    private final IdentityProvider provider = mock(IdentityProvider.class);
    private final IdentityHasher hasher = mock(IdentityHasher.class);
    private final IdentitySettings settings = new IdentitySettings("store", "channel", "secret", "x".repeat(32), Duration.ofMinutes(10));
    private final IdentityVerificationService service = new IdentityVerificationService(store, provider, hasher,
            settings, Clock.fixed(start.plusSeconds(3600), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(store.findOwned(request.id(), 1L)).thenReturn(request);
        when(store.findVerified(1L)).thenReturn(Optional.empty());
        when(hasher.hash("di")).thenReturn("hash");
        when(store.complete(eq(request), eq("hash"), any(), any())).thenAnswer(i ->
                new VerifiedIdentity(1L, request.id(), i.getArgument(2), i.getArgument(3)));
    }

    private IdentityProvider.Result result(String status, String channel, String type, Instant verifiedAt, String di) {
        return new IdentityProvider.Result(request.id(), status, "V2", channel, type, verifiedAt, di);
    }

    @Test
    void 유효시간_내_인증은_서버_장애로_늦게_처리해도_복구된다() {
        when(provider.lookup(request)).thenReturn(result("VERIFIED", "channel", "LIVE", start.plusSeconds(100), "di"));
        assertThat(service.complete(1L, request.id()).verified()).isTrue();
    }

    @Test
    void 외부_장애_후_같은_요청으로_재시도한다() {
        when(provider.lookup(request)).thenThrow(new BusinessException(PROVIDER_UNAVAILABLE))
                .thenReturn(result("VERIFIED", "channel", "LIVE", start.plusSeconds(100), "di"));
        assertThatThrownBy(() -> service.complete(1L, request.id())).isInstanceOf(BusinessException.class);
        verify(store, never()).complete(any(), any(), any(), any());
        assertThat(service.complete(1L, request.id()).verified()).isTrue();
    }

    @Test
    void 다른_계정의_요청은_외부_조회_전에_거절한다() {
        when(store.findOwned(request.id(), 2L)).thenThrow(new BusinessException(REQUEST_NOT_FOUND));
        assertThatThrownBy(() -> service.complete(2L, request.id())).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(REQUEST_NOT_FOUND);
        verifyNoInteractions(provider, hasher);
    }

    @Test
    void 완료된_요청은_외부_장애와_무관하게_동일한_성공을_반환한다() {
        when(store.findVerified(1L)).thenReturn(Optional.of(new VerifiedIdentity(1L, request.id(), start, start)));
        assertThat(service.complete(1L, request.id()).verified()).isTrue();
        verifyNoInteractions(provider, hasher);
    }

    @Test
    void 미완료_테스트채널_채널변조_DI누락_시각위조는_저장하지_않는다() {
        var invalid = java.util.List.of(
                result("READY", "channel", "LIVE", null, null),
                result("FAILED", "channel", "LIVE", null, null),
                result("VERIFIED", "channel", "TEST", start.plusSeconds(100), "di"),
                result("VERIFIED", "other", "LIVE", start.plusSeconds(100), "di"),
                result("VERIFIED", "channel", "LIVE", start.plusSeconds(100), null),
                result("VERIFIED", "channel", "LIVE", start.minusSeconds(1), "di"),
                result("VERIFIED", "channel", "LIVE", start.plusSeconds(3601), "di"),
                result("VERIFIED", "channel", "LIVE", start.plusSeconds(600), "di"),
                new IdentityProvider.Result("other", "VERIFIED", "V2", "channel", "LIVE", start, "di"),
                new IdentityProvider.Result(request.id(), "VERIFIED", "V1", "channel", "LIVE", start, "di"));
        for (var response : invalid) {
            when(provider.lookup(request)).thenReturn(response);
            assertThatThrownBy(() -> service.complete(1L, request.id())).isInstanceOf(BusinessException.class);
        }
        verify(store, never()).complete(any(), any(), any(), any());
    }

    @Test
    void 미인증_계정과_설정_누락은_허용하지_않는다() {
        assertThat(service.status(1L).verified()).isFalse();
        assertThatThrownBy(() -> service.requireVerified(1L)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(REQUIRED);
        var missing = new IdentitySettings("", "", "", "", Duration.ofMinutes(10));
        assertThatThrownBy(missing::requireConfigured).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(NOT_CONFIGURED);
    }
}
