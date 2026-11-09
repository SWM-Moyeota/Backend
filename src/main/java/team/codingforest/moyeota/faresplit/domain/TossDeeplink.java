package team.codingforest.moyeota.faresplit.domain;

import team.codingforest.moyeota.faresplit.domain.enums.Bank;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 *  토스 송금 화면을 금액·은행·계좌가 채워진 채로 여는 딥링크. 토스가 공개한 규격이 아니라 바뀌면 열리지 않을 수 있어
 *  앱은 계좌·금액 복사 버튼을 함께 보여 준다
 */
public final class TossDeeplink {
    static final String BASE = "supertoss://send";

    private TossDeeplink() {
    }

    public static String of(int amount, Bank bank, AccountNumber accountNumber) {
        return BASE + "?amount=" + amount
                + "&bank=" + encode(bank.tossName())
                + "&accountNo=" + accountNumber.value();
    }

    // URLEncoder 는 폼 방식이라 공백을 + 로 바꾼다 - 쿼리 값은 %20 이어야 한다
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
