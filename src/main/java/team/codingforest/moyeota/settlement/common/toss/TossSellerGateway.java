package team.codingforest.moyeota.settlement.common.toss;

import lombok.RequiredArgsConstructor;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.account.domain.BusinessInfo;
import team.codingforest.moyeota.settlement.account.domain.Seller;
import team.codingforest.moyeota.settlement.account.domain.SellerGateway;
import team.codingforest.moyeota.settlement.account.domain.SellerRegistration;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;

/**
 * 셀러 포트를 토스 지급대행 셀러 API 로 구현함
 */
@RequiredArgsConstructor
public class TossSellerGateway implements SellerGateway {
    private static final String SELLERS = "/v2/sellers";
    private static final String SELLER = "/v2/sellers/{id}";

    private final TossPayoutClient client;

    @Override
    public Seller register(SellerRegistration registration) {
        BusinessInfo info = registration.businessInfo();
        TossSellerRequest body = new TossSellerRequest(
                registration.refSellerId(),
                registration.businessType().name(),
                new TossSellerRequest.Company(info.name(), info.representativeName(), info.businessRegistrationNumber(), info.email(), info.phone()),
                account(registration.bankCode(), registration.accountNumber(), registration.accountHolder()));

        return toSeller(client.postEncrypted(SELLERS, body, null, TossSeller.class));
    }

    @Override
    public Seller changeAccount(String sellerId, BankCode bankCode, String accountNumber, String accountHolder) {
        TossSellerAccountUpdateRequest body = new TossSellerAccountUpdateRequest(account(bankCode, accountNumber, accountHolder));

        return toSeller(client.postEncrypted(SELLER, body, null, TossSeller.class, sellerId));
    }

    @Override
    public Seller find(String sellerId) {
        return toSeller(client.get(SELLER, TossSeller.class, sellerId));
    }

    private static TossSellerRequest.Account account(BankCode bankCode, String accountNumber, String accountHolder) {
        return new TossSellerRequest.Account(bankCode.getTossCode(), accountNumber, accountHolder);
    }

    private static Seller toSeller(TossSeller seller) {
        return new Seller(seller.id(), SellerStatus.valueOf(seller.status()));
    }
}
