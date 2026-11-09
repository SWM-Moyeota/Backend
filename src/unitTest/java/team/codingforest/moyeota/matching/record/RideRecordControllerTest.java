package team.codingforest.moyeota.matching.record;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import team.codingforest.moyeota.common.exception.GlobalExceptionHandler;
import team.codingforest.moyeota.matching.api.RideFares;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.party.PartyJpaTest;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.api.MemberSummary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RideRecordControllerTest {
    private static final Long 나 = 1L;
    private static final Long 동승자 = 2L;
    private static final Instant 기준 = Instant.parse("2026-10-01T10:00:00.123456Z");

    private PartyJpaTest parties;
    private FakeRideRecords rideRecords;
    private RideRecordServiceTest.StubUserAccess users;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        parties = new PartyJpaTest();
        rideRecords = new FakeRideRecords(parties);
        users = new RideRecordServiceTest.StubUserAccess();

        mvc = MockMvcBuilders.standaloneSetup(new RideRecordController(new RideRecordService(rideRecords, users, mock(RideFares.class))))
                .setCustomArgumentResolvers(new StubCurrentUserResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 토큰의_사용자_탑승_기록을_내려준다() throws Exception {
        MemberSummary 내정보 = users.등록(나, "성윤");
        users.등록(동승자, "지민");
        Long partyId = ride(기준.plusSeconds(1800), 나, 동승자);

        mvc.perform(get("/api/v1/users/me/parties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rides", hasSize(1)))
                .andExpect(jsonPath("$.rides[0].partyId").value(partyId))
                .andExpect(jsonPath("$.rides[0].departure").value("강남역"))
                .andExpect(jsonPath("$.rides[0].destination").value("판교역"))
                // 탑승 시각은 서버가 채우지 않고 요금은 택시비 정산이 없는 방이라 비지만, 출시 앱이 읽으므로 키는 남고 값은 null 이다
                .andExpect(jsonPath("$.rides[0]", hasKey("pickupTime")))
                .andExpect(jsonPath("$.rides[0].pickupTime").value(nullValue()))
                .andExpect(jsonPath("$.rides[0].dropoffTime").value(기준.plusSeconds(1800).toString()))
                .andExpect(jsonPath("$.rides[0]", hasKey("totalFare")))
                .andExpect(jsonPath("$.rides[0].totalFare").value(nullValue()))
                .andExpect(jsonPath("$.rides[0]", hasKey("farePerPerson")))
                .andExpect(jsonPath("$.rides[0].farePerPerson").value(nullValue()))
                .andExpect(jsonPath("$.rides[0].currentMembers").value(2))
                .andExpect(jsonPath("$.rides[0].members", hasSize(2)))
                .andExpect(jsonPath("$.rides[0].members[0].publicId").value(내정보.publicId().toString()))
                .andExpect(jsonPath("$.rides[0].members[0].nickname").value("성윤"))
                .andExpect(jsonPath("$.rides[0].members[0].imageUrl").value(내정보.imageUrl()))
                .andExpect(jsonPath("$.rides[0].members[1].nickname").value("지민"))
                .andExpect(jsonPath("$.rides[0].taxiDriverId").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void size_를_생략하면_20개씩_주고_nextCursor_로_다음_페이지를_받는다() throws Exception {
        for(int i = 0; i < 21; i++) {
            ride(기준.plusSeconds(i), 나);
        }

        String body = mvc.perform(get("/api/v1/users/me/parties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rides", hasSize(20)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        Number nextCursor = JsonPath.read(body, "$.nextCursor");
        Number lastPartyId = JsonPath.read(body, "$.rides[19].partyId");

        assertThat(nextCursor.longValue()).isEqualTo(lastPartyId.longValue());
        mvc.perform(get("/api/v1/users/me/parties").param("cursor", nextCursor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rides", hasSize(1)))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void 기록이_없으면_빈_배열을_내려준다() throws Exception {
        mvc.perform(get("/api/v1/users/me/parties").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rides", hasSize(0)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void size_가_범위_밖이면_400_INVALID_PAGE_SIZE() throws Exception {
        mvc.perform(get("/api/v1/users/me/parties").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));

        mvc.perform(get("/api/v1/users/me/parties").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
    }

    @Test
    void size_가_숫자가_아니면_400_INVALID_REQUEST() throws Exception {
        mvc.perform(get("/api/v1/users/me/parties").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("size: 형식이 올바르지 않습니다."));
    }

    @Test
    void cursor_가_없는_기록이면_400_INVALID_CURSOR() throws Exception {
        mvc.perform(get("/api/v1/users/me/parties").param("cursor", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void cursor_가_정수가_아니면_400_INVALID_REQUEST() throws Exception {
        mvc.perform(get("/api/v1/users/me/parties").param("cursor", "not-a-cursor!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("cursor: 형식이 올바르지 않습니다."));
    }

    @Test
    void cursor_가_빈_값이면_첫_페이지를_준다() throws Exception {
        Long partyId = ride(기준, 나);

        mvc.perform(get("/api/v1/users/me/parties").param("cursor", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rides", hasSize(1)))
                .andExpect(jsonPath("$.rides[0].partyId").value(partyId));
    }

    private Long ride(Instant dropoffTime, Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for(int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], 기준.plusSeconds(i), null));
        }
        Long partyId = parties.save(Party.load(null, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), members, members.getFirst().getMemberId(), 기준, PartyStatus.FINISHED, 12000, 25, "_p~iF~ps|U_ulLnnqC", 0L,
                RoomType.REALTIME, 기준)).getId();
        rideRecords.save(RideRecord.load(partyId, "강남역", "판교역", dropoffTime));
        return partyId;
    }

    static class StubCurrentUserResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                      NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return 나;
        }
    }
}
