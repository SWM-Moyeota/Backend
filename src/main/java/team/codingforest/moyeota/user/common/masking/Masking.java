package team.codingforest.moyeota.user.common.masking;

/**
 * 화면, 응답에 내보내는 개인정보 가림 처리. 저장 값에는 쓰지 않음
 */
public final class Masking {

    private Masking() {
    }

    /** 홍길동 → 홍*동, 홍길 → 홍*, 남궁민수 → 남**수 */
    public static String name(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() == 1) return "*";
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    /** hong@example.com → ho**@example.com, ab@x.com → a*@x.com */
    public static String email(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return email;
        int visible = Math.min(2, at / 2);
        return email.substring(0, visible) + "*".repeat(at - visible) + email.substring(at);
    }

    /** 01012345678 → 010-****-5678. PhoneNumber 로 정규화된 값 기준 */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 10) return phone;
        return phone.substring(0, 3) + "-****-" + phone.substring(phone.length() - 4);
    }
}
