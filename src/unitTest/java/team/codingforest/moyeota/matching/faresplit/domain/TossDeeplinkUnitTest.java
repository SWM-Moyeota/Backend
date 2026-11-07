package team.codingforest.moyeota.matching.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TossDeeplinkUnitTest {
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");

    @DisplayName("of - 금액·URL 인코딩한 토스 은행 이름·계좌번호를 쿼리로 붙인다")
    @Test
    void of() {

        /*
         * test
         */
        String link = TossDeeplink.of(6_240, Bank.KAKAO, ACCOUNT);

        /*
         * verify
         */
        assertThat(link).isEqualTo("supertoss://send?amount=6240&bank=%EC%B9%B4%EC%B9%B4%EC%98%A4%EB%B1%85%ED%81%AC&accountNo=3333012345678");
    }

    @DisplayName("of - 모든 은행의 링크는 bank 를 디코딩하면 토스 은행 이름으로 돌아오고 공백이 + 로 바뀌지 않는다")
    @ParameterizedTest
    @EnumSource(Bank.class)
    void of_everyBank(Bank bank) {

        /*
         * test
         */
        String link = TossDeeplink.of(10_000, bank, ACCOUNT);

        /*
         * verify
         */
        UriComponents uri = UriComponentsBuilder.fromUriString(link).build();
        assertThat(uri.getScheme()).isEqualTo("supertoss");
        assertThat(uri.getHost()).isEqualTo("send");
        assertThat(uri.getQueryParams().getFirst("amount")).isEqualTo("10000");
        assertThat(uri.getQueryParams().getFirst("accountNo")).isEqualTo("3333012345678");
        String encodedBank = uri.getQueryParams().getFirst("bank");
        assertThat(encodedBank).doesNotContain("+");
        assertThat(URLDecoder.decode(encodedBank, StandardCharsets.UTF_8)).isEqualTo(bank.tossName());
    }
}
