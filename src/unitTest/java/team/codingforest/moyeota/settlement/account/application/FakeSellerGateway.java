package team.codingforest.moyeota.settlement.account.application;

import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.account.domain.Seller;
import team.codingforest.moyeota.settlement.account.domain.SellerGateway;
import team.codingforest.moyeota.settlement.account.domain.SellerGatewayException;
import team.codingforest.moyeota.settlement.account.domain.SellerRegistration;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 받은 요청을 남기고 정해 둔 상태로 답하거나 정해 둔 이유로 실패함. 등록하면 셀러 id 는 seller_{refSellerId}
 */
public class FakeSellerGateway implements SellerGateway {
    private final List<SellerRegistration> registrations = new ArrayList<>();
    private final List<AccountChange> accountChanges = new ArrayList<>();
    private final List<String> finds = new ArrayList<>();
    private SellerStatus status = SellerStatus.PARTIALLY_APPROVED;
    private SellerGatewayException failure;

    public record AccountChange(String sellerId, BankCode bankCode, String accountNumber, String accountHolder) {
    }

    public FakeSellerGateway respondWith(SellerStatus status) {
        this.status = status;
        return this;
    }

    public FakeSellerGateway failWith(SellerGatewayException.Reason reason) {
        this.failure = new SellerGatewayException(reason, "TOSS_" + reason, null);
        return this;
    }

    public List<SellerRegistration> registrations() {
        return registrations;
    }

    public List<AccountChange> accountChanges() {
        return accountChanges;
    }

    public List<String> finds() {
        return finds;
    }

    public boolean untouched() {
        return registrations.isEmpty() && accountChanges.isEmpty() && finds.isEmpty();
    }

    @Override
    public Seller register(SellerRegistration registration) {
        registrations.add(registration);
        if(failure != null) throw failure;
        return new Seller("seller_" + registration.refSellerId(), status);
    }

    @Override
    public Seller changeAccount(String sellerId, BankCode bankCode, String accountNumber, String accountHolder) {
        accountChanges.add(new AccountChange(sellerId, bankCode, accountNumber, accountHolder));
        if(failure != null) throw failure;
        return new Seller(sellerId, status);
    }

    @Override
    public Seller find(String sellerId) {
        finds.add(sellerId);
        if(failure != null) throw failure;
        return new Seller(sellerId, status);
    }
}
