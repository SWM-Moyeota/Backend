package team.codingforest.moyeota.faresplit.domain;

import team.codingforest.moyeota.faresplit.domain.enums.Bank;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class TossDeeplink {
    static final String BASE = "supertoss://send";

    private TossDeeplink() {
    }

    public static String of(int amount, Bank bank, AccountNumber accountNumber) {
        return BASE + "?amount=" + amount
                + "&bank=" + encode(bank.tossName())
                + "&accountNo=" + accountNumber.value();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
