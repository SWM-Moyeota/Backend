package team.codingforest.moyeota.matching.faresplit.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.util.regex.Pattern;

/**
 *  받을 계좌번호 - 숫자만 10~16자리. 정산마다 입력받고 프로필에는 남기지 않는다
 */
public record AccountNumber(String value) {
    public static final int MIN_DIGITS = 10;
    public static final int MAX_DIGITS = 16;
    private static final Pattern DIGITS = Pattern.compile("\\d{" + MIN_DIGITS + "," + MAX_DIGITS + "}");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s-]");

    public AccountNumber {
        if(value == null || !DIGITS.matcher(value).matches()) throw new BusinessException(MatchingErrorCode.INVALID_ACCOUNT_NUMBER);
    }

    /** 사용자가 넣은 값. 은행 앱에서 복사해 온 하이픈·공백은 떼고, 그 밖의 문자가 섞이면 거절한다 */
    public static AccountNumber of(String raw) {
        if(raw == null) throw new BusinessException(MatchingErrorCode.INVALID_ACCOUNT_NUMBER);

        return new AccountNumber(SEPARATORS.matcher(raw).replaceAll(""));
    }

    // 로그에 계좌번호 전체가 찍히지 않게 끝 4자리만 남긴다
    @Override
    public String toString() {
        return "AccountNumber[****" + value.substring(value.length() - 4) + "]";
    }
}
