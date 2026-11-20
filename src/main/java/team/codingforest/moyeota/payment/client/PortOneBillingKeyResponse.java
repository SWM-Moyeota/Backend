package team.codingforest.moyeota.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import team.codingforest.moyeota.payment.dto.CardDetail;

import java.util.List;

/** GET /billing-keys/{billingKey} 응답. status 는 ISSUED | DELETED */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneBillingKeyResponse(String status, Customer customer, List<Method> methods) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String id) {}

    /** type 은 BillingKeyPaymentMethodCard / BillingKeyPaymentMethodEasyPay / BillingKeyPaymentMethodMobile ... */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Method(String type, Card card) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(String name, String number, String brand) {}

    public boolean isIssued() {
        return "ISSUED".equals(status);
    }

    public String customerId() {
        return customer == null ? null : customer.id();
    }

    /** 빌링키에 묶인 카드. 간편결제·휴대폰 등 카드가 아니면 null */
    public CardDetail cardOrNull() {
        if(methods == null) return null;

        for(Method method : methods) {
            if(method != null && "BillingKeyPaymentMethodCard".equals(method.type()) && method.card() != null) {
                String number = method.card().number();
                // 외부 응답도 원문 번호가 들어올 가능성에 대비해 마지막 네 자리 외 숫자를 가린다.
                String masked = number == null ? null : number.replaceAll("\\d(?=(?:\\D*\\d){4})", "*");
                return new CardDetail(method.card().name(), masked, method.card().brand());
            }
        }

        return null;
    }
}
