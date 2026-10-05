package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.user.auth.infrastructure.JwtProvider;
import team.codingforest.moyeota.user.common.domain.User;
import team.codingforest.moyeota.user.common.domain.Users;
import team.codingforest.moyeota.user.common.domain.enums.LoginType;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  방 상태 API 를 실제 필터 체인으로 본다. 경로가 /matching/rooms/{partyId} 아래라
 *  매핑 충돌·인가 누락·응답 필드 이름은 서비스 단위 테스트로는 잡히지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PartyStatusApiTest {
    @Autowired MockMvc mvc;
    @Autowired Parties parties;
    @Autowired Users users;
    @Autowired JwtProvider jwtProvider;

    @Test
    void 상태와_현재_인원만_돌려준다() throws Exception {
        Party party = Party.open(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
        party.join(2L);
        Long partyId = parties.save(party).getId();

        mvc.perform(get("/api/v1/matching/rooms/{id}/status", partyId).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currentMembers").value(2))
                .andExpect(jsonPath("$.members").doesNotExist())   // 상세와 달리 멤버·경로는 싣지 않는다
                .andExpect(jsonPath("$.route").doesNotExist());
    }

    @Test
    void 없는_방은_404() throws Exception {
        mvc.perform(get("/api/v1/matching/rooms/{id}/status", 999_999L).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mvc.perform(get("/api/v1/matching/rooms/{id}/status", 1L))
                .andExpect(status().isUnauthorized());
    }

    private String bearer() {
        User user = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL));
        return "Bearer " + jwtProvider.issuePair(user.getPublicId(), UUID.randomUUID(), Instant.now()).access();
    }
}
