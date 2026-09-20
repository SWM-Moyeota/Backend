package team.codingforest.moyeota.chat.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import team.codingforest.moyeota.chat.application.MessageSearchService;
import team.codingforest.moyeota.chat.application.dto.MessageSearchPage;
import team.codingforest.moyeota.chat.domain.search.MessageSearchQuery;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.application.AuthService;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MessageSearchControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean MessageSearchService service;
    private final String path = "/api/v1/chat-rooms/1/messages/search/advanced";

    @Test void 익명_검색은_401이다() throws Exception {
        mvc.perform(get(path).param("keyword", "출구")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void 사용자_ID는_쿼리가_아닌_인증_주체에서_가져온다() throws Exception {
        when(auth.authenticate("valid-token")).thenReturn(new AuthenticatedPrincipal(7L, UUID.randomUUID()));
        when(service.search(eq(7L), any())).thenReturn(new MessageSearchPage(List.of(), null, false));
        mvc.perform(get(path).header("Authorization", "Bearer valid-token").param("keyword", "출구")
                        .param("userId", "999").param("senderId", "8").param("from", "2026-09-20T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("hasNext").value(false));
        verify(service).search(7L, new MessageSearchQuery(1L, "출구", 8L,
                Instant.parse("2026-09-20T00:00:00Z"), null, null, 30));
    }

    @Test void 잘못된_기간은_400이다() throws Exception {
        when(auth.authenticate("valid-token")).thenReturn(new AuthenticatedPrincipal(7L, UUID.randomUUID()));
        mvc.perform(get(path).header("Authorization", "Bearer valid-token").param("keyword", "출구")
                        .param("from", "2026-09-21T00:00:00Z").param("until", "2026-09-20T00:00:00Z"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
