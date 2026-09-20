package team.codingforest.moyeota.place.application;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.application.dto.AddressResponse;
import team.codingforest.moyeota.place.domain.Address;
import team.codingforest.moyeota.place.domain.AddressCache;
import team.codingforest.moyeota.place.domain.RegionSearcher;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReverseGeocodingApplicationTest {

    private static final Address 강남대로 = new Address("서울특별시 강남구 강남대로 396", "서울특별시 강남구 역삼동 858");

    /** 메모리 캐시 - Redis 없이 서비스의 캐시 사용 순서와 키 반올림을 검증 */
    static class FakeCache implements AddressCache {
        final Map<String, Address> store = new HashMap<>();

        @Override
        public Optional<Address> find(double latitude, double longitude) {
            return Optional.ofNullable(store.get(latitude + "," + longitude));
        }

        @Override
        public void save(double latitude, double longitude, Address address) {
            store.put(latitude + "," + longitude, address);
        }
    }

    private static final RegionSearcher 호출되면_안_됨 = (lat, lng) -> { throw new AssertionError("외부 조회가 호출되면 안 된다"); };

    @Test
    void 도로명이_있으면_대표_주소로_도로명을_쓴다() {
        ReverseGeocodingApplication service = new ReverseGeocodingApplication((lat, lng) -> Optional.of(강남대로), new FakeCache());

        AddressResponse response = service.findAddress(37.4979, 127.0276);

        assertThat(response.address()).isEqualTo("서울특별시 강남구 강남대로 396");
    }

    @Test
    void 도로명이_없으면_지번이_대표_주소가_된다() {
        ReverseGeocodingApplication service = new ReverseGeocodingApplication(
                (lat, lng) -> Optional.of(new Address(null, "경기도 광주시 오포읍 신현리 123-4")), new FakeCache());

        assertThat(service.findAddress(37.36, 127.23).address()).isEqualTo("경기도 광주시 오포읍 신현리 123-4");
    }

    @Test
    void 주소가_없는_좌표는_404가_난다() {
        ReverseGeocodingApplication service = new ReverseGeocodingApplication((lat, lng) -> Optional.empty(), new FakeCache());

        assertThatThrownBy(() -> service.findAddress(35.0, 129.9))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void 한국_밖_좌표는_외부_호출_전에_차단된다() {
        // 검색기가 호출되면 실패하는 가짜 - 가드가 네이버 호출 "전"에 있음을 함께 검증
        ReverseGeocodingApplication service = new ReverseGeocodingApplication(호출되면_안_됨, new FakeCache());

        assertThatThrownBy(() -> service.findAddress(35.6762, 139.6503))   // 도쿄
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATES);
    }

    @Test
    void 같은_좌표를_다시_조회하면_외부_API_를_호출하지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        ReverseGeocodingApplication service = new ReverseGeocodingApplication(
                (lat, lng) -> { calls.incrementAndGet(); return Optional.of(강남대로); }, new FakeCache());

        service.findAddress(37.4979, 127.0276);
        AddressResponse second = service.findAddress(37.4979, 127.0276);

        assertThat(calls).hasValue(1);
        assertThat(second.address()).isEqualTo("서울특별시 강남구 강남대로 396");
    }

    @Test
    void 약_11m_안의_근처_좌표는_같은_캐시_키로_묶인다() {
        // GPS 가 같은 자리에서 조금씩 다르게 찍혀도 소수 4자리로 반올림하면 한 키
        AtomicInteger calls = new AtomicInteger();
        FakeCache cache = new FakeCache();
        ReverseGeocodingApplication service = new ReverseGeocodingApplication(
                (lat, lng) -> { calls.incrementAndGet(); return Optional.of(강남대로); }, cache);

        service.findAddress(37.49791, 127.02762);
        service.findAddress(37.49794, 127.02758);

        assertThat(calls).hasValue(1);
        assertThat(cache.store).containsOnlyKeys("37.4979,127.0276");
    }

    @Test
    void 외부_호출은_반올림하지_않은_원래_좌표로_나간다() {
        double[] seen = new double[2];
        ReverseGeocodingApplication service = new ReverseGeocodingApplication(
                (lat, lng) -> { seen[0] = lat; seen[1] = lng; return Optional.of(강남대로); }, new FakeCache());

        service.findAddress(37.49791, 127.02762);

        assertThat(seen[0]).isEqualTo(37.49791);
        assertThat(seen[1]).isEqualTo(127.02762);
    }

    @Test
    void 캐시에_있으면_외부_API_없이_바로_응답한다() {
        FakeCache cache = new FakeCache();
        cache.save(37.4979, 127.0276, 강남대로);
        ReverseGeocodingApplication service = new ReverseGeocodingApplication(호출되면_안_됨, cache);

        assertThat(service.findAddress(37.4979, 127.0276).address()).isEqualTo("서울특별시 강남구 강남대로 396");
    }

    @Test
    void 주소를_못_찾은_좌표는_캐시에_남기지_않는다() {
        FakeCache cache = new FakeCache();
        ReverseGeocodingApplication service = new ReverseGeocodingApplication((lat, lng) -> Optional.empty(), cache);

        assertThatThrownBy(() -> service.findAddress(35.0, 129.9)).isInstanceOf(BusinessException.class);

        assertThat(cache.store).isEmpty();
    }
}
