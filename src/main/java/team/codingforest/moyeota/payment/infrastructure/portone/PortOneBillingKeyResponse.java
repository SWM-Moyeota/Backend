package team.codingforest.moyeota.payment.infrastructure.portone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import team.codingforest.moyeota.payment.domain.CardDetail;
import team.codingforest.moyeota.payment.domain.EasyPayDetail;
import team.codingforest.moyeota.payment.domain.MethodDetail;
import team.codingforest.moyeota.payment.domain.MobileDetail;

import java.util.List;

/** GET /billing-keys/{billingKey} */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneBillingKeyResponse(String status, Customer customer, List<Method> methods) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String id) {}

    /** type: BillingKeyPaymentMethodCard / BillingKeyPaymentMethodEasyPay / BillingKeyPaymentMethodMobile ... */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Method(String type, Card card, String provider, String phoneNumber) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(String name, String number, String brand) {}

    public boolean isIssued() {
        return "ISSUED".equals(status);
    }

    public String customerId() {
        return customer == null ? null : customer.id();
    }

    /** 첫 번째로 지원하는 수단. 계좌이체·페이팔 등 우리가 안 받는 종류면 null */
    public MethodDetail firstSupportedDetail() {
        if(methods == null) return null;

        for(Method method : methods) {
            if(method.type() == null) continue;

            if(method.type().endsWith("Card") && method.card() != null) {
                return new CardDetail(method.card().name(), method.card().number(), method.card().brand());
            }
            if(method.type().endsWith("EasyPay")) {
                return new EasyPayDetail(method.provider());
            }
            if(method.type().endsWith("Mobile")) {
                return new MobileDetail(method.phoneNumber());
            }
        }

        return null;
    }
}
