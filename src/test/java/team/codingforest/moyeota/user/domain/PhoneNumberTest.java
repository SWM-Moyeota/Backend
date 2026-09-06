package team.codingforest.moyeota.user.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

import static org.assertj.core.api.Assertions.*;

class PhoneNumberTest {

    @Test
    void 하이픈_유무에_관계없이_같은_값으로_정규화된다() {
        // 가입은 "010-1234-5678", 확인은 "01012345678"로 와도 같은 번호로 취급되어야 phone-first 온보딩이 성립한다
        assertThat(new PhoneNumber("010-1234-5678").value())
                .isEqualTo(new PhoneNumber("01012345678").value())
                .isEqualTo("01012345678");
    }

    @Test
    void 앞뒤_공백은_제거된다() {
        assertThat(new PhoneNumber(" 010-1234-5678 ").value()).isEqualTo("01012345678");
    }

    @Test
    void 열_자리_옛_번호도_허용된다() {
        assertThat(new PhoneNumber("011-123-4567").value()).isEqualTo("0111234567");
    }

    @Test
    void 휴대폰_번호_형식이_아니면_거부된다() {
        for(String invalid : new String[]{"02-123-4567", "010-12-3456", "1012345678", "010-1234-56789", "abc", ""}) {
            assertThatThrownBy(() -> new PhoneNumber(invalid))
                    .as("입력: '%s'", invalid)
                    .isInstanceOf(UserException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.INVALID_PHONE_NUMBER);
        }
    }

    @Test
    void null이면_NPE가_아니라_도메인_예외가_난다() {
        assertThatThrownBy(() -> new PhoneNumber(null))
                .isInstanceOf(UserException.class);
    }
}
