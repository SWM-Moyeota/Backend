package team.codingforest.moyeota.place.domain;

import java.util.Optional;

/** 좌표 → 역지오코딩 주소 캐시. 키는 호출 측에서 반올림한 좌표(근처 좌표를 같은 키로 묶기 위해). */
public interface AddressCache {
    Optional<Address> find(double latitude, double longitude);
    void save(double latitude, double longitude, Address address);
}
