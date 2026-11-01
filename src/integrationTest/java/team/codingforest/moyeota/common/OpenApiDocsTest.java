package team.codingforest.moyeota.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  프론트가 보는 문서가 실제 계약과 어긋나는 세 가지를 막는다:
 *  토큰 없이 문서를 못 보는 것, 토큰에서 오는 파라미터가 쿼리로 노출되는 것, 인증 그룹에 Bearer 가 요구되는 것.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {
    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode docs() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    void 문서와_UI는_토큰_없이_열린다() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());   // → /swagger-ui/index.html
    }

    @Test
    void 토큰에서_주입되는_파라미터는_스펙에_나오지_않는다() throws Exception {
        JsonNode join = docs().at("/paths/~1api~1v1~1matching~1rooms~1{partyId}~1join/post");
        List<String> names = new ArrayList<>();
        join.path("parameters").forEach(p -> names.add(p.path("name").asText()));

        assertThat(names).as("@CurrentUser memberId 가 쿼리 파라미터로 잡히면 프론트가 헛값을 보낸다").containsExactly("partyId");

        JsonNode accept = docs().at("/paths/~1api~1v1~1dispatch~1calls~1{partyId}~1accept/post");
        List<String> driverParams = new ArrayList<>();
        accept.path("parameters").forEach(p -> driverParams.add(p.path("name").asText()));
        assertThat(driverParams).as("@CurrentDriver driverId 도 마찬가지").containsExactly("partyId");
    }

    @Test
    void 인증_그룹은_Bearer를_요구하지_않고_나머지는_요구한다() throws Exception {
        JsonNode d = docs();
        assertThat(d.at("/paths/~1api~1v1~1auth~1login/post/security").isArray()).isTrue();
        assertThat(d.at("/paths/~1api~1v1~1auth~1login/post/security")).isEmpty();
        assertThat(d.at("/security/0/bearerAuth").isMissingNode()).as("전역 Bearer 요구사항").isFalse();
    }

    @Test
    void 에러_응답에는_공통_ErrorResponse_스키마가_붙는다() throws Exception {
        JsonNode notFound = docs().at("/paths/~1api~1v1~1matching~1rooms~1{partyId}/get/responses/404/content/application~1json/schema/$ref");
        assertThat(notFound.asText()).isEqualTo("#/components/schemas/ErrorResponse");
    }

    @Test
    void 인증이_필요한_API에만_401이_붙는다() throws Exception {
        JsonNode d = docs();
        assertThat(d.at("/paths/~1api~1v1~1matching~1rooms~1{partyId}/get/responses/401").isMissingNode()).isFalse();
        assertThat(d.at("/paths/~1api~1v1~1auth~1phone~1check/post/responses/401").isMissingNode()).isTrue();
        assertThat(d.at("/paths/~1api~1v1~1auth~1sms~1send/post/responses/401").isMissingNode()).isTrue();
        assertThat(d.at("/paths/~1api~1v1~1auth~1sms~1send/post/security").isArray()).isTrue();
        assertThat(d.at("/paths/~1api~1v1~1auth~1sms~1send/post/security")).isEmpty();
        assertThat(d.at("/paths/~1api~1v1~1config/get/responses/401").isMissingNode()).isTrue();
        assertThat(d.at("/paths/~1api~1v1~1config/get/security").isArray()).isTrue();
        assertThat(d.at("/paths/~1api~1v1~1config/get/security")).isEmpty();
    }

    @Test
    void 내_탑승_기록은_cursor_size_만_쿼리로_받는다() throws Exception {
        JsonNode myRides = docs().at("/paths/~1api~1v1~1users~1me~1parties/get");
        List<String> names = new ArrayList<>();
        myRides.path("parameters").forEach(p -> names.add(p.path("name").asText()));

        assertThat(names).containsExactly("cursor", "size");
        assertThat(myRides.at("/parameters/0/required").asBoolean()).isFalse();
        assertThat(myRides.at("/parameters/0/schema/type").asText()).isEqualTo("integer");
        assertThat(myRides.at("/parameters/1/schema/type").asText()).isEqualTo("integer");
        assertThat(myRides.at("/parameters/1/schema/default").asInt()).isEqualTo(20);
        assertThat(myRides.at("/responses/401").isMissingNode()).isFalse();
        assertThat(myRides.at("/responses/400/content/application~1json/schema/$ref").asText()).isEqualTo("#/components/schemas/ErrorResponse");
    }

    @Test
    void 탑승_기록의_멤버_스키마는_방_상세의_멤버_스키마와_따로_잡히고_기사_식별자가_없다() throws Exception {
        JsonNode schemas = docs().at("/components/schemas");

        assertThat(schemas.at("/MemberInfo/properties/rideCount").isMissingNode()).isFalse();
        assertThat(schemas.at("/RideRecordMember/properties/publicId").isMissingNode()).isFalse();
        assertThat(schemas.at("/RideRecordMember/properties/rideCount").isMissingNode()).isTrue();
        assertThat(schemas.at("/RideRecordItem/properties/farePerPerson").isMissingNode()).isFalse();
        assertThat(schemas.at("/RideRecordItem/properties/taxiDriverId").isMissingNode()).isTrue();
    }
}
