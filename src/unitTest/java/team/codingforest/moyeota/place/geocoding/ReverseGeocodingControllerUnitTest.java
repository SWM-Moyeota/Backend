package team.codingforest.moyeota.place.geocoding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;
import team.codingforest.moyeota.place.geocoding.dto.AddressResponse;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReverseGeocodingController.class)
class ReverseGeocodingControllerUnitTest extends ControllerUnitTestSupport {
    private static final String URL = "/api/v1/places/reverse";
    private static final double LATITUDE = 37.4979;
    private static final double LONGITUDE = 127.0276;

    @MockitoBean
    private ReverseGeocodingService service;

    @DisplayName("GET /places/reverse - 쿼리의 좌표로 조회한 주소를 반환")
    @Test
    void reverse() throws Exception {

        /*
         * settings
         */
        final AddressResponse response = new AddressResponse("서울 강남구 테헤란로 152", "서울 강남구 테헤란로 152", "서울 강남구 역삼동 737");
        doReturn(response).when(service).findAddress(eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("latitude", "37.4979").param("longitude", "127.0276"))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("서울 강남구 테헤란로 152"))
                .andExpect(jsonPath("$.roadAddress").value("서울 강남구 테헤란로 152"))
                .andExpect(jsonPath("$.jibunAddress").value("서울 강남구 역삼동 737"));
        verify(service).findAddress(eq(LATITUDE), eq(LONGITUDE));
    }

    @DisplayName("GET /places/reverse - 도로명이 없으면 roadAddress 는 null")
    @Test
    void reverse_noRoadAddress() throws Exception {

        /*
         * settings
         */
        final AddressResponse response = new AddressResponse("서울 강남구 역삼동 737", null, "서울 강남구 역삼동 737");
        doReturn(response).when(service).findAddress(eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("latitude", "37.4979").param("longitude", "127.0276"))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("서울 강남구 역삼동 737"))
                .andExpect(jsonPath("$.roadAddress").value(nullValue()));
    }

    @DisplayName("GET /places/reverse - 토큰이 없으면 401")
    @Test
    void reverse_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(URL).param("latitude", "37.4979").param("longitude", "127.0276"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(service);
    }

    @DisplayName("GET /places/reverse - latitude 가 없으면 400")
    @Test
    void reverse_noLatitude() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("longitude", "127.0276"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @DisplayName("GET /places/reverse - longitude 가 없으면 400")
    @Test
    void reverse_noLongitude() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("latitude", "37.4979"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @DisplayName("GET /places/reverse - latitude 가 숫자가 아니면 400")
    @Test
    void reverse_latitudeNotNumber() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("latitude", "abc").param("longitude", "127.0276"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("latitude: 형식이 올바르지 않습니다."));
        verifyNoInteractions(service);
    }

    @DisplayName("GET /places/reverse - longitude 가 숫자가 아니면 400")
    @Test
    void reverse_longitudeNotNumber() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("latitude", "37.4979").param("longitude", "abc"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("longitude: 형식이 올바르지 않습니다."));
        verifyNoInteractions(service);
    }

    @DisplayName("GET /places/reverse - 범위 밖 좌표면 400")
    @Test
    void reverse_invalidCoordinates() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(PlaceErrorCode.INVALID_COORDINATES)).when(service).findAddress(eq(0.0), eq(0.0));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("latitude", "0").param("longitude", "0"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));
    }

    @DisplayName("GET /places/reverse - 주소가 없으면 404")
    @Test
    void reverse_addressNotFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(PlaceErrorCode.ADDRESS_NOT_FOUND)).when(service).findAddress(eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("latitude", "37.4979").param("longitude", "127.0276"))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
    }

    @DisplayName("GET /places/reverse - 외부 API 실패면 502")
    @Test
    void reverse_failed() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(PlaceErrorCode.REVERSE_GEOCODE_FAILED)).when(service).findAddress(eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("latitude", "37.4979").param("longitude", "127.0276"))

        /*
         * verify
         */
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("REVERSE_GEOCODE_FAILED"));
    }
}
