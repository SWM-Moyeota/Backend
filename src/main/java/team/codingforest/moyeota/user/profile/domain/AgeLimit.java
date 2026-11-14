package team.codingforest.moyeota.user.profile.domain;

import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 *  가입 나이 제한 - 만 14세 미만은 가입할 수 없다. 앱에서만 막으면 API 를 직접 부르는 쪽이 그대로 통과하므로 서버에서 본다.
 *  <p>
 *  연도 빼기가 아니라 만 나이다 - 2012-12-31 생은 2026-01-01 에 아직 13세다.
 *  날짜는 한국 시각(KST) 기준으로 센다. UTC 로 세면 한국 사용자의 날짜가 하루 어긋난다.
 *  14번째 생일 당일은 통과이고, 그 전날까지만 막는다.
 *  <p>
 *  윤년은 {@code plusYears} 의 규칙을 그대로 쓴다 - 2008-02-29 생은 2022-02-28 에 만 14세가 된다(민법 제160조 ③).
 *  {@code Period} 로 세면 법적으로 이미 14세인 하루를 더 막아 버린다.
 */
public record AgeLimit() {
    public static final int MINIMUM_AGE = 14;
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** today 에 만 14세가 안 됐는지 - 14번째 생일 당일부터 false */
    public static boolean isUnderMinimum(LocalDate birthDate, LocalDate today) {
        return today.isBefore(birthDate.plusYears(MINIMUM_AGE));
    }

    /** 만 14세 미만이면 UNDER_MINIMUM_AGE(USER113). 두 시각 모두 KST 날짜로 바꿔서 센다 */
    public static void check(Instant birthDate, Instant now) {
        if(isUnderMinimum(toKstDate(birthDate), toKstDate(now))) {
            throw new UserException(UserErrorCode.UNDER_MINIMUM_AGE);
        }
    }

    private static LocalDate toKstDate(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }
}
