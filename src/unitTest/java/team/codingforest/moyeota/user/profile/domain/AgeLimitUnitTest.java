package team.codingforest.moyeota.user.profile.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  가입 나이 제한. 한국 시각 날짜로 센 만 나이가 14세 미만이면 막고, 14번째 생일 당일은 통과다
 */
class AgeLimitUnitTest {

    // ───────────────────────── isUnderMinimum - 생일 경계 ─────────────────────────

    @DisplayName("isUnderMinimum - 14번째 생일 전날까지는 미만이고 생일 당일부터는 아니다")
    @ParameterizedTest(name = "{0} 생이 {1} 에 → 미만={2}")
    @CsvSource({
            "2010-06-15, 2024-06-14, true",     // 생일 하루 전
            "2010-06-15, 2024-06-15, false",    // 생일 당일 - 통과
            "2010-06-15, 2024-06-16, false",    // 생일 다음 날
            "2010-06-15, 2024-06-01, true",
            "2010-06-15, 2030-01-01, false"
    })
    void isUnderMinimum_birthdayBoundary(LocalDate 생년월일, LocalDate 오늘, boolean 미만) {

        /*
         * test & verify
         */
        assertThat(AgeLimit.isUnderMinimum(생년월일, 오늘)).isEqualTo(미만);
    }

    @DisplayName("isUnderMinimum - 연도만 빼면 통과해 버리는 날도 생일 전이면 미만이다")
    @Test
    void isUnderMinimum_notYearSubtraction() {

        /*
         * settings
         */
        // 2026 - 2012 = 14 지만 2026-01-01 에는 아직 13세다
        LocalDate 생년월일 = LocalDate.of(2012, 12, 31);

        /*
         * test & verify
         */
        assertThat(AgeLimit.isUnderMinimum(생년월일, LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(AgeLimit.isUnderMinimum(생년월일, LocalDate.of(2026, 12, 30))).isTrue();
        assertThat(AgeLimit.isUnderMinimum(생년월일, LocalDate.of(2026, 12, 31))).isFalse();
    }

    // ───────────────────────── isUnderMinimum - 윤일 ─────────────────────────

    @DisplayName("isUnderMinimum - 2월 29일 생은 평년 2월 28일에 만 14세가 된다(민법 제160조 ③)")
    @Test
    void isUnderMinimum_leapDay() {

        /*
         * settings
         */
        // plusYears 는 2008-02-29 + 14년을 2022-02-28 로 맞춘다. Period 로 세면 13년 11개월 30일이라
        // 법적으로 이미 14세인 2022-02-28 하루를 더 막아 버린다
        LocalDate 생년월일 = LocalDate.of(2008, 2, 29);

        /*
         * test & verify
         */
        assertThat(생년월일.plusYears(AgeLimit.MINIMUM_AGE)).isEqualTo(LocalDate.of(2022, 2, 28));
        assertThat(AgeLimit.isUnderMinimum(생년월일, LocalDate.of(2022, 2, 27))).isTrue();
        assertThat(AgeLimit.isUnderMinimum(생년월일, LocalDate.of(2022, 2, 28))).isFalse();
        assertThat(AgeLimit.isUnderMinimum(생년월일, LocalDate.of(2022, 3, 1))).isFalse();
    }

    // ───────────────────────── check - KST 기준 ─────────────────────────

    @DisplayName("check - 생년월일이 UTC 로는 전날이어도 KST 날짜로 센다")
    @Test
    void check_birthDateResolvesByKst() {

        /*
         * settings
         */
        // UTC 로는 2008-03-01, KST 로는 2008-03-02 → KST 기준 14번째 생일은 2022-03-02
        Instant 생년월일 = Instant.parse("2008-03-01T15:30:00Z");
        Instant 생일_전날 = Instant.parse("2022-03-01T05:00:00Z");   // UTC·KST 모두 2022-03-01

        /*
         * test & verify
         */
        // UTC 날짜로 셌다면 2008-03-01 + 14년 = 2022-03-01 이라 통과해 버린다
        assertThatThrownBy(() -> AgeLimit.check(생년월일, 생일_전날))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.UNDER_MINIMUM_AGE);
        assertThatCode(() -> AgeLimit.check(생년월일, Instant.parse("2022-03-02T05:00:00Z"))).doesNotThrowAnyException();
    }

    @DisplayName("check - 지금이 UTC 로는 전날이어도 KST 로 생일이 지났으면 통과한다")
    @Test
    void check_nowResolvesByKst() {

        /*
         * settings
         */
        Instant 생년월일 = Instant.parse("2008-03-02T05:00:00Z");    // UTC·KST 모두 2008-03-02
        Instant 생일_당일 = Instant.parse("2022-03-01T15:30:00Z");   // UTC 로는 2022-03-01, KST 로는 2022-03-02

        /*
         * test & verify
         */
        // UTC 날짜로 셌다면 2022-03-01 < 2022-03-02 라 거부해 버린다
        assertThatCode(() -> AgeLimit.check(생년월일, 생일_당일)).doesNotThrowAnyException();
        assertThatThrownBy(() -> AgeLimit.check(생년월일, Instant.parse("2022-02-28T15:30:00Z")))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.UNDER_MINIMUM_AGE);
    }

    @DisplayName("check - 만 14세 미만이면 UNDER_MINIMUM_AGE(USER113) 400 이다")
    @Test
    void check_errorCode() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(
                () -> AgeLimit.check(Instant.parse("2010-06-15T00:00:00Z"), Instant.parse("2024-06-14T00:00:00Z")));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(UserException.class);
        assertThat(((UserException) thrown).getErrorCode()).isEqualTo(UserErrorCode.UNDER_MINIMUM_AGE);
        assertThat(((UserException) thrown).getErrorCode().getCode()).isEqualTo("USER113");
    }
}
