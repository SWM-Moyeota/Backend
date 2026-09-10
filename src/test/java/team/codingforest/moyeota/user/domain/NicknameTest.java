package team.codingforest.moyeota.user.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknameTest {

    @Test
    void 앞뒤_공백은_제거된다() {
        // " 모여타 " 와 "모여타" 가 다른 닉네임으로 취급되면 중복 확인이 무의미해진다
        assertThat(new Nickname(" 모여타 ").value()).isEqualTo("모여타");
    }

    @Test
    void 한글_영문_숫자_조합은_허용된다() {
        assertThat(new Nickname("moyeota12").value()).isEqualTo("moyeota12");
        assertThat(new Nickname("모여타A1").value()).isEqualTo("모여타A1");
    }

    @Test
    void 두_자_미만이나_열_자_초과는_거부된다() {
        assertThatThrownBy(() -> new Nickname("가")).isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_NICKNAME);
        assertThatThrownBy(() -> new Nickname("가나다라마바사아자차카")).isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_NICKNAME);
    }

    @Test
    void 특수문자와_중간_공백은_거부된다() {
        assertThatThrownBy(() -> new Nickname("모여!타")).isInstanceOf(UserException.class);
        assertThatThrownBy(() -> new Nickname("모여 타")).isInstanceOf(UserException.class);
    }

    @Test
    void null이면_거부된다() {
        assertThatThrownBy(() -> new Nickname(null)).isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_NICKNAME);
    }
}
