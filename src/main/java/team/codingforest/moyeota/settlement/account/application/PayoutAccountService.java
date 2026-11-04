package team.codingforest.moyeota.settlement.account.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccount;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccounts;
import team.codingforest.moyeota.settlement.account.domain.Seller;
import team.codingforest.moyeota.settlement.account.domain.SellerGateway;
import team.codingforest.moyeota.settlement.account.domain.SellerGatewayException;
import team.codingforest.moyeota.settlement.account.domain.SellerRegistration;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;
import team.codingforest.moyeota.settlement.account.dto.PayoutAccountRegisterCommand;
import team.codingforest.moyeota.settlement.account.dto.PayoutAccountResponse;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * 기사 지급 계좌 등록 수정 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.settlement.toss", name = "enabled", havingValue = "true")
public class PayoutAccountService {
    private static final Set<SellerStatus> PENDING = Set.of(SellerStatus.APPROVAL_REQUIRED, SellerStatus.KYC_REQUIRED);

    private final DriverPayoutAccounts accounts;
    private final SellerGateway sellerGateway;

    /**
     * 우리 행을 먼저 저장해 refSellerId 를 정하고 결제사 셀러로 등록함. 결제사 등록이 실패하면 행을 지움
     *
     * @param taxiDriverId 기사 id
     * @param command      등록 내용
     * @return 등록된 지급 계좌
     */
    public PayoutAccountResponse register(Long taxiDriverId, PayoutAccountRegisterCommand command) {
        DriverPayoutAccount account = prepare(taxiDriverId, command);

        Seller seller;

        try {
            seller = sellerGateway.register(new SellerRegistration(account.refSellerId(), account.getBusinessType(),
                    command.businessInfo(), command.bankCode(), command.accountNumber(), command.accountHolder()));
        } catch (SellerGatewayException e) {
            accounts.delete(account);
            throw toBusinessException(taxiDriverId, e);
        }

        account.registerSeller(seller.sellerId(), seller.status());

        return PayoutAccountResponse.from(accounts.save(account));
    }

    /**
     * 결제사 셀러 계좌를 먼저 바꾸고 성공하면 우리 행도 바꿈. 바뀐 계좌로 셀러 상태가 달라질 수 있어 응답 상태로 덮음
     *
     * @param taxiDriverId  기사 id
     * @param bankCode      은행
     * @param accountNumber 계좌번호 원문
     * @param accountHolder 예금주
     * @return 바뀐 지급 계좌
     */
    public PayoutAccountResponse changeAccount(Long taxiDriverId, BankCode bankCode, String accountNumber, String accountHolder) {
        DriverPayoutAccount account = accounts.findByDriverId(taxiDriverId)
                .filter(DriverPayoutAccount::isSellerRegistered)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.PAYOUT_ACCOUNT_NOT_FOUND));

        account.changeAccount(bankCode, accountNumber, accountHolder);

        Seller seller;

        try {
            seller = sellerGateway.changeAccount(account.getSellerId(), bankCode, accountNumber, accountHolder);
        } catch (SellerGatewayException e) {
            throw toBusinessException(taxiDriverId, e);
        }

        account.changeSellerStatus(seller.status());

        return PayoutAccountResponse.from(accounts.save(account));
    }

    /**
     * 승인 대기 중인 셀러면 결제사에서 상태를 다시 읽음. 결제사가 응답하지 않으면 저장된 상태를 그대로 줌
     *
     * @param taxiDriverId 기사 id
     * @return 지급 계좌
     */
    public PayoutAccountResponse find(Long taxiDriverId) {
        DriverPayoutAccount account = accounts.findByDriverId(taxiDriverId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.PAYOUT_ACCOUNT_NOT_FOUND));

        if(!account.isSellerRegistered() || !PENDING.contains(account.getSellerStatus())) {
            return PayoutAccountResponse.from(account);
        }

        try {
            Seller seller = sellerGateway.find(account.getSellerId());

            if(seller.status() == account.getSellerStatus()) {
                return PayoutAccountResponse.from(account);
            }

            account.changeSellerStatus(seller.status());

            return PayoutAccountResponse.from(accounts.save(account));
        } catch (SellerGatewayException e) {
            log.warn("셀러 상태 갱신 실패 taxiDriverId={}, reason={}, code={}", taxiDriverId, e.getReason(), e.getCode(), e);

            return PayoutAccountResponse.from(account);
        }
    }

    /**
     * 셀러 없이 남은 행은 등록 제한 시간이 지났을 때만 버려진 것으로 보고 지움. 그 전이면 다른 요청이 결제사를 부르는 중
     *
     * @param taxiDriverId 기사 id
     * @param command      등록 내용
     * @return 새로 저장한 지급 계좌
     */
    private DriverPayoutAccount prepare(Long taxiDriverId, PayoutAccountRegisterCommand command) {
        Optional<DriverPayoutAccount> found = accounts.findByDriverId(taxiDriverId);

        if(found.isPresent()) {
            DriverPayoutAccount existing = found.get();

            if(existing.isSellerRegistered()) {
                throw new BusinessException(SettlementErrorCode.PAYOUT_ACCOUNT_ALREADY_REGISTERED);
            }
            if(!existing.isAbandonedRegistration(Instant.now())) {
                throw new BusinessException(SettlementErrorCode.PAYOUT_ACCOUNT_REGISTERING);
            }
            accounts.delete(existing);
        }

        return insert(taxiDriverId, command);
    }

    private DriverPayoutAccount insert(Long taxiDriverId, PayoutAccountRegisterCommand command) {
        DriverPayoutAccount account = DriverPayoutAccount.create(taxiDriverId, command.businessType(), command.bankCode(),
                command.accountNumber(), command.accountHolder());

        try {
            return accounts.save(account);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(SettlementErrorCode.PAYOUT_ACCOUNT_ALREADY_REGISTERED);
        }
    }

    private static BusinessException toBusinessException(Long taxiDriverId, SellerGatewayException e) {
        log.warn("셀러 요청 실패 taxiDriverId={}, reason={}, code={}", taxiDriverId, e.getReason(), e.getCode(), e);

        return new BusinessException(switch(e.getReason()) {
            case REJECTED -> SettlementErrorCode.PAYOUT_ACCOUNT_REJECTED;
            case LOCKED -> SettlementErrorCode.PAYOUT_ACCOUNT_LOCKED;
            case UNAVAILABLE -> SettlementErrorCode.PAYOUT_PROVIDER_UNAVAILABLE;
        });
    }
}
