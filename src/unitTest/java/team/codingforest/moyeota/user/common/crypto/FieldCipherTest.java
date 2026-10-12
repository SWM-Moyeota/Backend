package team.codingforest.moyeota.user.common.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCipherTest {
    private static final String KEY = "XgIsaebhS+OFGOUd4l16AnNPZgXIydBInUv5yyQvmMY=";

    private final FieldCipher cipher = new FieldCipher(KEY);

    @Test
    void 암호화한_값을_복호화하면_원래_값이다() {
        String encrypted = cipher.encrypt("hong@example.com");

        assertThat(encrypted).startsWith("v1:").doesNotContain("hong");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("hong@example.com");
    }

    @Test
    void 한글도_그대로_돌아온다() {
        assertThat(cipher.decrypt(cipher.encrypt("홍길동"))).isEqualTo("홍길동");
    }

    @Test
    void 같은_값도_암호화할_때마다_결과가_다르다() {
        // IV 가 매번 달라야 같은 이름을 가진 행끼리 암호문으로 묶이지 않는다
        assertThat(cipher.encrypt("홍길동")).isNotEqualTo(cipher.encrypt("홍길동"));
    }

    @Test
    void 접두사가_없는_평문은_그대로_돌려준다() {
        assertThat(cipher.decrypt("hong@example.com")).isEqualTo("hong@example.com");
    }

    @Test
    void 암호문이_바뀌면_복호화가_실패한다() {
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("홍길동").substring(3));
        bytes[bytes.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 다른_키로는_복호화할_수_없다() {
        FieldCipher other = new FieldCipher(Base64.getEncoder().encodeToString(new byte[32]));

        assertThatThrownBy(() -> other.decrypt(cipher.encrypt("홍길동"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키가_32바이트가_아니면_생성할_수_없다() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new FieldCipher(shortKey)).isInstanceOf(IllegalArgumentException.class);
    }
}
