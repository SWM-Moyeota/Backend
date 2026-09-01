package team.codingforest.moyeota.place.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.domain.Address;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 *  네이버 응답 조각 → 완성 주소 조립 검증.
 *  MockRestServiceServer로 실제 HTTP 왕복 없이 요청 형식(좌표 순서!)과 응답 파싱을 함께 잠근다.
 */
class NaverReverseGeoCodingClientTest {

    private MockRestServiceServer server;
    private NaverReverseGeoCodingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://maps.apigw.ntruss.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NaverReverseGeoCodingClient(builder.build());
    }

    @Test
    void 좌표는_경도_위도_순서로_요청한다() {
        // D-1에서 팀이 데인 그 함정 - 순서가 바뀌면 지구 반대편이 조회된다
        server.expect(queryParam("coords", "127.0276,37.4979"))
                .andExpect(queryParam("output", "json"))
                .andRespond(withSuccess(응답_없음(), MediaType.APPLICATION_JSON));

        client.find(37.4979, 127.0276);

        server.verify();
    }

    @Test
    void 도로명과_지번을_각각_조립한다() {
        server.expect(anything()).andRespond(withSuccess("""
                {"status":{"code":0},"results":[
                  {"name":"roadaddr",
                   "region":{"area1":{"name":"서울특별시"},"area2":{"name":"강남구"},"area3":{"name":"역삼동"},"area4":{"name":""}},
                   "land":{"name":"강남대로","number1":"396","number2":"","addition0":{"value":"강남역빌딩"}}},
                  {"name":"addr",
                   "region":{"area1":{"name":"서울특별시"},"area2":{"name":"강남구"},"area3":{"name":"역삼동"},"area4":{"name":""}},
                   "land":{"name":"","number1":"858","number2":"","addition0":{"value":""}}}
                ]}""", MediaType.APPLICATION_JSON));

        Address address = client.find(37.4979, 127.0276).orElseThrow();

        assertThat(address.roadAddress()).isEqualTo("서울특별시 강남구 강남대로 396 강남역빌딩");
        assertThat(address.jibunAddress()).isEqualTo("서울특별시 강남구 역삼동 858");
    }

    @Test
    void 도로명이_없는_지역은_지번으로_폴백한다() {
        // 도로명주소 미부여 지역 - roadaddr 항목 자체가 응답에서 빠진다
        server.expect(anything()).andRespond(withSuccess("""
                {"status":{"code":0},"results":[
                  {"name":"addr",
                   "region":{"area1":{"name":"경기도"},"area2":{"name":"광주시"},"area3":{"name":"오포읍"},"area4":{"name":"신현리"}},
                   "land":{"name":"","number1":"123","number2":"4","addition0":{"value":""}}}
                ]}""", MediaType.APPLICATION_JSON));

        Address address = client.find(37.36, 127.23).orElseThrow();

        assertThat(address.roadAddress()).isNull();
        assertThat(address.jibunAddress()).isEqualTo("경기도 광주시 오포읍 신현리 123-4");   // 리 포함 + 부번 하이픈
        assertThat(address.display()).isEqualTo("경기도 광주시 오포읍 신현리 123-4");        // 폴백 표시
    }

    @Test
    void 주소가_없는_좌표는_빈_결과를_돌려준다() {
        // 바다 한가운데 - 장애가 아니라 정상적인 "없음"이므로 예외가 아니어야 한다
        server.expect(anything()).andRespond(withSuccess(응답_없음(), MediaType.APPLICATION_JSON));

        Optional<Address> result = client.find(35.0, 129.9);

        assertThat(result).isEmpty();
    }

    @Test
    void 네이버_장애는_규격_안의_주소_조회_실패_예외로_변환된다() {
        server.expect(anything()).andRespond(withServerError());

        assertThatThrownBy(() -> client.find(37.4979, 127.0276))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.REVERSE_GEOCODE_FAILED);
    }

    private String 응답_없음() {
        return """
                {"status":{"code":3},"results":[]}""";   // 3 = no results
    }
}
