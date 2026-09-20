package team.codingforest.moyeota.user.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import team.codingforest.moyeota.user.application.IdentityVerificationService;
import team.codingforest.moyeota.user.domain.*;
import team.codingforest.moyeota.user.domain.enums.LoginType;
import team.codingforest.moyeota.user.infrastructure.JwtProvider;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "portone.identity.store-id=store", "portone.identity.channel-key=channel", "portone.api.secret=test-secret",
        "portone.identity.di-hash-key=01234567890123456789012345678901"})
@AutoConfigureMockMvc
class IdentityVerificationControllerTest {
    private static final String PATH = "/api/v1/users/me/identity-verifications";
    @Autowired MockMvc mvc;
    @Autowired Users users;
    @Autowired JwtProvider jwt;
    @Autowired IdentityVerificationService service;
    @MockitoBean IdentityProvider provider;

    private User user() { return users.save(User.from(UUID.randomUUID(), LoginType.LOCAL)); }
    private String token(User user) {
        return "Bearer " + jwt.issuePair(user.getPublicId(), UUID.randomUUID(), Instant.now()).access();
    }

    @Test
    void 본인인증_API는_모두_로그인이_필요하다() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH + "/iv-test/complete")).andExpect(status().isUnauthorized());
        verifyNoInteractions(provider);
    }

    @Test
    void 인증된_로그인_계정에만_결과를_연결하고_민감정보는_응답하지_않는다() throws Exception {
        var owner = user(); var attacker = user();
        var request = service.start(owner.getId());
        mvc.perform(post(PATH + "/" + request.id() + "/complete").header("Authorization", token(attacker)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("IDENTITY002"));
        verifyNoInteractions(provider);
        when(provider.lookup(request)).thenReturn(new IdentityProvider.Result(request.id(), "VERIFIED", "V2",
                "channel", "LIVE", request.createdAt(), "di-" + UUID.randomUUID()));
        mvc.perform(post(PATH + "/" + request.id() + "/complete").header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("verified").value(true))
                .andExpect(jsonPath("verifiedAt").exists()).andExpect(jsonPath("di").doesNotExist());
        mvc.perform(get(PATH).header("Authorization", token(attacker)))
                .andExpect(status().isOk()).andExpect(jsonPath("verified").value(false));
    }

    @Test
    void 로그인만으로는_매칭방에_참가할_수_없다() throws Exception {
        var owner = user();
        mvc.perform(post("/api/v1/matching/rooms/123/join").header("Authorization", token(owner)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("IDENTITY001"));
        verifyNoInteractions(provider);
    }

    @Test
    void 시작_응답에는_SDK_입력값만_포함된다() throws Exception {
        mvc.perform(post(PATH).header("Authorization", token(user())))
                .andExpect(status().isOk()).andExpect(jsonPath("identityVerificationId").isString())
                .andExpect(jsonPath("storeId").value("store")).andExpect(jsonPath("channelKey").value("channel"))
                .andExpect(jsonPath("expiresAt").exists()).andExpect(jsonPath("userId").doesNotExist())
                .andExpect(jsonPath("secret").doesNotExist());
    }
}
