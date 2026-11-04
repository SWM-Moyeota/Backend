package team.codingforest.moyeota.settlement.account.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.account.domain.BusinessType;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccount;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccounts;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.profiles.active=dev")
class DriverPayoutAccountPostgresTest {
    private static final String 계좌번호 = "110123456789";
    private static final String 예금주 = "모여타택시";

    @Autowired DriverPayoutAccounts accounts;
    @Autowired JdbcTemplate jdbc;

    private final Long 기사 = ThreadLocalRandom.current().nextLong(5_000_000_000L, 6_000_000_000L);
    private final Long 다른기사 = 기사 + 1;
    private final String 셀러 = "seller_" + 기사;

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM driver_payout_account WHERE taxi_driver_id BETWEEN ? AND ?", 기사, 다른기사);
    }

    @Test
    void 새_계좌를_저장하면_id_와_생성_수정_시각이_채워지고_기사로_다시_찾을_수_있다() {
        DriverPayoutAccount saved = accounts.save(새_계좌(기사));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        DriverPayoutAccount found = accounts.findByDriverId(기사).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getBusinessType()).isEqualTo(BusinessType.CORPORATE);
        assertThat(found.getBankCode()).isEqualTo(BankCode.KOOKMIN);
        assertThat(found.getMaskedAccountNumber()).isEqualTo("********6789");
        assertThat(found.getAccountHolder()).isEqualTo(예금주);
        assertThat(found.getSellerId()).isNull();
        assertThat(found.getSellerStatus()).isNull();
    }

    @Test
    void 계좌번호_원문은_DB_에_남지_않는다() {
        accounts.save(새_계좌(기사));

        Integer rawRows = jdbc.queryForObject(
                "SELECT count(*) FROM driver_payout_account WHERE taxi_driver_id = ? AND masked_account_number LIKE ?",
                Integer.class, 기사, "%" + 계좌번호 + "%");
        assertThat(rawRows).isZero();
    }

    @Test
    void 트랜잭션_없이_불러도_셀러_등록이_DB_에_반영된다() {
        DriverPayoutAccount saved = accounts.save(새_계좌(기사));

        saved.registerSeller(셀러, SellerStatus.PARTIALLY_APPROVED);
        accounts.save(saved);

        DriverPayoutAccount found = accounts.findByDriverId(기사).orElseThrow();
        assertThat(found.getSellerId()).isEqualTo(셀러);
        assertThat(found.getSellerStatus()).isEqualTo(SellerStatus.PARTIALLY_APPROVED);
        assertThat(found.canReceivePayout()).isTrue();
    }

    @Test
    void 계좌를_바꾸면_은행_가린_번호_예금주가_바뀌고_기사와_사업자_유형은_그대로다() {
        DriverPayoutAccount saved = accounts.save(새_계좌(기사));

        saved.changeAccount(BankCode.SHINHAN, "1002003004005", "새예금주");
        accounts.save(saved);

        DriverPayoutAccount found = accounts.findByDriverId(기사).orElseThrow();
        assertThat(found.getBankCode()).isEqualTo(BankCode.SHINHAN);
        assertThat(found.getMaskedAccountNumber()).isEqualTo("*********4005");
        assertThat(found.getAccountHolder()).isEqualTo("새예금주");
        assertThat(found.getTaxiDriverId()).isEqualTo(기사);
        assertThat(found.getBusinessType()).isEqualTo(BusinessType.CORPORATE);
    }

    @Test
    void 셀러_id_로_계좌를_찾는다() {
        DriverPayoutAccount saved = accounts.save(새_계좌(기사));
        saved.registerSeller(셀러, SellerStatus.PARTIALLY_APPROVED);
        accounts.save(saved);

        assertThat(accounts.findBySellerId(셀러)).get()
                .extracting(DriverPayoutAccount::getTaxiDriverId)
                .isEqualTo(기사);
        assertThat(accounts.findBySellerId("seller_none_" + 기사)).isEmpty();
    }

    @Test
    void 계좌를_지우면_다시_찾을_수_없고_같은_기사로_새로_만들_수_있다() {
        DriverPayoutAccount saved = accounts.save(새_계좌(기사));

        accounts.delete(saved);

        assertThat(accounts.findByDriverId(기사)).isEmpty();
        assertThat(accounts.save(새_계좌(기사)).getId()).isNotEqualTo(saved.getId());
    }

    @Test
    void 기사_한_명에게_지급_계좌는_하나만_만들_수_있다() {
        accounts.save(새_계좌(기사));

        assertThatThrownBy(() -> accounts.save(새_계좌(기사))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_토스_셀러를_두_계좌에_등록할_수_없다() {
        DriverPayoutAccount first = accounts.save(새_계좌(기사));
        first.registerSeller(셀러, SellerStatus.PARTIALLY_APPROVED);
        accounts.save(first);

        DriverPayoutAccount second = accounts.save(새_계좌(다른기사));
        second.registerSeller(셀러, SellerStatus.PARTIALLY_APPROVED);

        assertThatThrownBy(() -> accounts.save(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static DriverPayoutAccount 새_계좌(Long driverId) {
        return DriverPayoutAccount.create(driverId, BusinessType.CORPORATE, BankCode.KOOKMIN, 계좌번호, 예금주);
    }
}
