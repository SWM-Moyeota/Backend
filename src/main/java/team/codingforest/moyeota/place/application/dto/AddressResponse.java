package team.codingforest.moyeota.place.application.dto;

import team.codingforest.moyeota.place.domain.Address;

public record AddressResponse(String address, String roadAddress, String jibunAddress) {
    public static AddressResponse from(Address a) {
        return new AddressResponse(a.display(), a.roadAddress(), a.jibunAddress());
    }
}
