// place/domain/Address.java
package team.codingforest.moyeota.place.domain;

public record Address(String roadAddress, String jibunAddress) {

    public String display() {
        return roadAddress != null ? roadAddress : jibunAddress;
    }
}