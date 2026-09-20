package team.codingforest.moyeota.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskerTest {

    @Test
    void 이메일은_로컬파트_양끝만_남긴다() {
        assertThat(LogMasker.email("john@example.com")).isEqualTo("j**n@example.com");
        assertThat(LogMasker.email("jo@example.com")).isEqualTo("**@example.com");   // 짧으면 전부
        assertThat(LogMasker.email("not-an-email")).isEqualTo("***");
        assertThat(LogMasker.email(null)).isNull();
    }

    @Test
    void 전화번호는_앞_3자리와_뒤_4자리만_남기고_구분자는_유지한다() {
        assertThat(LogMasker.phone("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(LogMasker.phone("01012345678")).isEqualTo("010****5678");
        assertThat(LogMasker.phone("1234567")).isEqualTo("***4567");   // 10자리 미만은 앞자리도 가린다
        assertThat(LogMasker.phone("5678")).isEqualTo("***");   // 남길 게 전부라 가린다
        assertThat(LogMasker.phone(null)).isNull();
    }

    @Test
    void 토큰은_앞_8자만_남긴다() {
        assertThat(LogMasker.token("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.abc")).isEqualTo("eyJhbGci***");
        assertThat(LogMasker.token("short-token")).isEqualTo("***");
        assertThat(LogMasker.token(null)).isNull();
    }
}
