package team.codingforest.moyeota.common.logging;

/**
 *  로그에 개인정보·비밀을 원문으로 남기지 않기 위한 마스킹.
 *  비밀번호·카드번호·주민번호·OTP 는 마스킹해서도 남기지 않는다 - 여기 메서드가 없는 이유.
 */
public final class LogMasker {
    private static final String MASK = "***";

    private LogMasker() {
    }

    /** john@example.com → j**n@example.com. 로컬 파트가 짧으면 전부 가린다 */
    public static String email(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return MASK;

        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() < 3) return "**" + domain;
        return local.charAt(0) + "**" + local.charAt(local.length() - 1) + domain;
    }

    /** 010-1234-5678 → 010-****-5678. 앞 3자리(식별번호)와 뒤 4자리만 남기고 구분자는 유지한다 */
    public static String phone(String phone) {
        if (phone == null) return null;
        int digits = (int) phone.chars().filter(Character::isDigit).count();
        if (digits <= 4) return MASK;
        int keepHead = digits >= 10 ? 3 : 0;   // 짧은 번호는 뒤 4자리만

        StringBuilder masked = new StringBuilder(phone.length());
        int seen = 0;
        for (char c : phone.toCharArray()) {
            if (Character.isDigit(c)) {
                boolean keep = seen < keepHead || digits - seen <= 4;
                masked.append(keep ? c : '*');
                seen++;
            } else {
                masked.append(c);
            }
        }
        return masked.toString();
    }

    /** 토큰·API 키는 앞 8자만: eyJhbGciOiJIUzI1NiJ9... → eyJhbGci***. 짧으면 전부 가린다 */
    public static String token(String token) {
        if (token == null) return null;
        if (token.length() < 16) return MASK;
        return token.substring(0, 8) + MASK;
    }
}
