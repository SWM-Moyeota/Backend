package team.codingforest.moyeota.place.geocoding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;
import team.codingforest.moyeota.place.geocoding.domain.Address;
import team.codingforest.moyeota.place.geocoding.domain.RegionSearcher;
import team.codingforest.moyeota.place.geocoding.dto.AddressResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReverseGeocodingServiceUnitTest {
    private static final double LATITUDE = 37.4979;
    private static final double LONGITUDE = 127.0276;
    private static final String ROAD = "서울 강남구 테헤란로 152";
    private static final String JIBUN = "서울 강남구 역삼동 737";

    @Mock
    private RegionSearcher regionSearcher;
    @InjectMocks
    private ReverseGeocodingService service;

    @DisplayName("findAddress - 도로명이 있으면 표시용 주소는 도로명")
    @Test
    void findAddress_road() {

        /*
         * settings
         */
        doReturn(Optional.of(new Address(ROAD, JIBUN))).when(regionSearcher).find(eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        AddressResponse response = service.findAddress(LATITUDE, LONGITUDE);

        /*
         * verify
         */
        assertThat(response.address()).isEqualTo(ROAD);
        assertThat(response.roadAddress()).isEqualTo(ROAD);
        assertThat(response.jibunAddress()).isEqualTo(JIBUN);
    }

    @DisplayName("findAddress - 도로명이 없으면 표시용 주소는 지번")
    @Test
    void findAddress_jibunOnly() {

        /*
         * settings
         */
        doReturn(Optional.of(new Address(null, JIBUN))).when(regionSearcher).find(eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        AddressResponse response = service.findAddress(LATITUDE, LONGITUDE);

        /*
         * verify
         */
        assertThat(response.address()).isEqualTo(JIBUN);
        assertThat(response.roadAddress()).isNull();
        assertThat(response.jibunAddress()).isEqualTo(JIBUN);
    }

    @DisplayName("findAddress - 서비스 범위 경계의 좌표는 허용")
    @ParameterizedTest
    @CsvSource({"33.0, 124.0", "33.0, 132.0", "39.0, 124.0", "39.0, 132.0"})
    void findAddress_boundary(double latitude, double longitude) {

        /*
         * settings
         */
        doReturn(Optional.of(new Address(ROAD, JIBUN))).when(regionSearcher).find(eq(latitude), eq(longitude));

        /*
         * test
         */
        AddressResponse response = service.findAddress(latitude, longitude);

        /*
         * verify
         */
        assertThat(response.address()).isEqualTo(ROAD);
    }

    @DisplayName("findAddress - 서비스 범위 밖의 좌표는 외부 API 를 부르지 않고 실패")
    @ParameterizedTest
    @CsvSource({
            "32.99999, 127.0",
            "39.00001, 127.0",
            "37.0, 123.99999",
            "37.0, 132.00001",
            "0.0, 0.0",
            "-37.0, 127.0",
            "37.0, -127.0",
            "Infinity, 127.0",
            "37.0, -Infinity",
            "NaN, 127.0",
            "37.0, NaN",
            "NaN, NaN"
    })
    void findAddress_outOfRange(double latitude, double longitude) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.findAddress(latitude, longitude));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.INVALID_COORDINATES);
        verifyNoInteractions(regionSearcher);
    }

    @DisplayName("findAddress - 좌표에 주소가 없으면 실패")
    @Test
    void findAddress_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(regionSearcher).find(eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.findAddress(LATITUDE, LONGITUDE));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.ADDRESS_NOT_FOUND);
    }

    @DisplayName("findAddress - 외부 API 실패 예외는 그대로 전파")
    @Test
    void findAddress_searcherFailed() {

        /*
         * settings
         */
        doThrow(new BusinessException(PlaceErrorCode.REVERSE_GEOCODE_FAILED)).when(regionSearcher).find(eq(LATITUDE), eq(LONGITUDE));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.findAddress(LATITUDE, LONGITUDE));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.REVERSE_GEOCODE_FAILED);
    }
}
