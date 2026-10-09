package team.codingforest.moyeota.user.common.masking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingTest {

    @Test
    void 이름은_첫_글자와_마지막_글자만_남긴다() {
        assertThat(Masking.name("홍길동")).isEqualTo("홍*동");
        assertThat(Masking.name("남궁민수")).isEqualTo("남**수");
    }

    @Test
    void 두_글자_이름은_첫_글자만_남긴다() {
        assertThat(Masking.name("홍길")).isEqualTo("홍*");
    }

    @Test
    void 한_글자_이름은_전부_가린다() {
        assertThat(Masking.name("홍")).isEqualTo("*");
    }

    @Test
    void 이메일은_아이디_앞_최대_두_글자만_남긴다() {
        assertThat(Masking.email("hong@example.com")).isEqualTo("ho**@example.com");
        assertThat(Masking.email("ab@x.com")).isEqualTo("a*@x.com");
        assertThat(Masking.email("a@x.com")).isEqualTo("*@x.com");
    }

    @Test
    void 전화번호는_가운데를_가린다() {
        assertThat(Masking.phone("01012345678")).isEqualTo("010-****-5678");
        assertThat(Masking.phone("0111234567")).isEqualTo("011-****-4567");
    }

    @Test
    void null은_그대로_돌려준다() {
        assertThat(Masking.name(null)).isNull();
        assertThat(Masking.email(null)).isNull();
        assertThat(Masking.phone(null)).isNull();
    }
}
