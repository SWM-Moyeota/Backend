package team.codingforest.moyeota.place.geocoding.dto;

import team.codingforest.moyeota.place.geocoding.domain.Address;

public record AddressResponse(String address, String roadAddress, String jibunAddress) {
    public static AddressResponse from(Address a) {
        return new AddressResponse(a.display(), a.roadAddress(), a.jibunAddress());
    }
}
