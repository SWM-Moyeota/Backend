package team.codingforest.moyeota.settlement.account.infrastructure.toss;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.account.domain.BusinessInfo;
import team.codingforest.moyeota.settlement.account.domain.Seller;
import team.codingforest.moyeota.settlement.account.domain.SellerGateway;
import team.codingforest.moyeota.settlement.account.domain.SellerGatewayException;
import team.codingforest.moyeota.settlement.account.domain.SellerRegistration;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;
import team.codingforest.moyeota.settlement.common.toss.TossPayoutClient;
import team.codingforest.moyeota.settlement.common.toss.TossPayoutException;

import java.util.Set;
import java.util.function.Supplier;

/**
 * 셀러 포트를 토스 지급대행 셀러 API 로 구현함. 토스 에러는 SellerGatewayException 으로 바꿔 던짐
 */
@ConditionalOnProperty(prefix = "moyeota.settlement.toss", name = "enabled", havingValue = "true")
@Component
@RequiredArgsConstructor
public class TossSellerGateway implements SellerGateway {
    private static final String SELLERS = "/v2/sellers";
    private static final String SELLER = "/v2/sellers/{id}";
    private static final String MODIFY_LOCKED = "FORBIDDEN_SELLER_MODIFY";
    private static final Set<HttpStatus> NOT_DRIVER_FAULT_STATUSES = Set.of(HttpStatus.UNAUTHORIZED, HttpStatus.TOO_MANY_REQUESTS);
    private static final Set<String> NOT_DRIVER_FAULT_CODES = Set.of("FORBIDDEN_REQUEST", "INVALID_ENCRYPTION", "PREVIOUS_REQUEST_IN_PROGRESS");

    private final TossPayoutClient client;

    @Override
    public Seller register(SellerRegistration registration) {
        BusinessInfo info = registration.businessInfo();
        TossSellerRequest body = new TossSellerRequest(
                registration.refSellerId(),
                registration.businessType().name(),
                new TossSellerRequest.Company(info.name(), info.representativeName(), info.businessRegistrationNumber(), info.email(), info.phone()),
                account(registration.bankCode(), registration.accountNumber(), registration.accountHolder()));

        return call(() -> toSeller(client.postEncrypted(SELLERS, body, null, TossSeller.class)));
    }

    @Override
    public Seller changeAccount(String sellerId, BankCode bankCode, String accountNumber, String accountHolder) {
        TossSellerAccountUpdateRequest body = new TossSellerAccountUpdateRequest(account(bankCode, accountNumber, accountHolder));

        return call(() -> toSeller(client.postEncrypted(SELLER, body, null, TossSeller.class, sellerId)));
    }

    @Override
    public Seller find(String sellerId) {
        return call(() -> toSeller(client.get(SELLER, TossSeller.class, sellerId)));
    }

    private static Seller call(Supplier<Seller> request) {
        try {
            return request.get();
        } catch (TossPayoutException e) {
            throw new SellerGatewayException(reasonOf(e), e.getCode(), e);
        } catch (RestClientException e) {
            throw new SellerGatewayException(SellerGatewayException.Reason.UNAVAILABLE, null, e);
        }
    }

    /**
     * 기사가 고쳐서 다시 보낼 수 있는 4xx 만 REJECTED 로 봄. 권한·암호화 같은 우리 쪽 문제와 처리 중 중복 요청은 UNAVAILABLE
     *
     * @param e 토스 에러
     * @return 셀러 요청 실패 이유
     */
    private static SellerGatewayException.Reason reasonOf(TossPayoutException e) {
        if(MODIFY_LOCKED.equals(e.getCode())) return SellerGatewayException.Reason.LOCKED;

        HttpStatus status = HttpStatus.resolve(e.getHttpStatus());
        boolean driverFault = status != null && status.is4xxClientError()
                && !NOT_DRIVER_FAULT_STATUSES.contains(status)
                && e.getCode() != null && !NOT_DRIVER_FAULT_CODES.contains(e.getCode());
        return driverFault ? SellerGatewayException.Reason.REJECTED : SellerGatewayException.Reason.UNAVAILABLE;
    }

    private static TossSellerRequest.Account account(BankCode bankCode, String accountNumber, String accountHolder) {
        return new TossSellerRequest.Account(bankCode.getTossCode(), accountNumber, accountHolder);
    }

    private static Seller toSeller(TossSeller seller) {
        return new Seller(seller.id(), SellerStatus.valueOf(seller.status()));
    }
}
