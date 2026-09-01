package team.codingforest.moyeota.place.application;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.application.dto.AddressResponse;
import team.codingforest.moyeota.place.domain.Address;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class ReverseGeocodingApplicationTest {

    @Test
    void 도로명이_있으면_대표_주소로_도로명을_쓴다() {
        ReverseGeocodingApplication service = new ReverseGeocodingApplication(
                (lat, lng) -> Optional.of(new Address("서울특별시 강남구 강남대로 396", "서울특별시 강남구 역삼동 858")));

        AddressResponse response = service.findAddress(37.4979, 127.0276);

        assertThat(response.address()).isEqualTo("서울특별시 강남구 강남대로 396");
    }

    @Test
    void 도로명이_없으면_지번이_대표_주소가_된다() {
        ReverseGeocodingApplication service = new ReverseGeocodingApplication(
                (lat, lng) -> Optional.of(new Address(null, "경기도 광주시 오포읍 신현리 123-4")));

        assertThat(service.findAddress(37.36, 127.23).address()).isEqualTo("경기도 광주시 오포읍 신현리 123-4");
    }

    @Test
    void 주소가_없는_좌표는_404가_난다() {
        ReverseGeocodingApplication service = new ReverseGeocodingApplication((lat, lng) -> Optional.empty());

        assertThatThrownBy(() -> service.findAddress(35.0, 129.9))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void 한국_밖_좌표는_외부_호출_전에_차단된다() {
        // 검색기가 호출되면 실패하는 가짜 - 가드가 네이버 호출 "전"에 있음을 함께 검증
        ReverseGeocodingApplication service = new ReverseGeocodingApplication(
                (lat, lng) -> { throw new AssertionError("외부 조회가 호출되면 안 된다"); });

        assertThatThrownBy(() -> service.findAddress(35.6762, 139.6503))   // 도쿄
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATES);
    }
}
