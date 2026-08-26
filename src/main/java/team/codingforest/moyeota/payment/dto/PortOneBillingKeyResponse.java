package team.codingforest.moyeota.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneBillingKeyResponse(String status, Customer customer, List<Method> methods) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Method(String type, Card card) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(String name, String number, String brand) {}

    public boolean isIssued() {
        return "ISSUED".equals(status);
    }

    /** 첫 번째 카드 정보 (없으면 null) */
    public Card firstCard() {
        if(methods == null) return null;
        return methods.stream()
                .map(Method::card)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
