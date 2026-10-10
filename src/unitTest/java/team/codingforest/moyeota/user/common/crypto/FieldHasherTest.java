package team.codingforest.moyeota.user.common.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldHasherTest {

    private final FieldHasher hasher = new FieldHasher("test-hash-key");

    @Test
    void 같은_값은_항상_같은_해시가_나온다() {
        // 저장할 때와 조회할 때 해시가 같아야 phone_hash 로 찾을 수 있다
        assertThat(hasher.hash("01012345678")).isEqualTo(hasher.hash("01012345678")).hasSize(64);
    }

    @Test
    void 다른_값은_다른_해시가_나온다() {
        assertThat(hasher.hash("01012345678")).isNotEqualTo(hasher.hash("01012345679"));
    }

    @Test
    void 키가_다르면_해시도_다르다() {
        assertThat(new FieldHasher("other-key").hash("01012345678")).isNotEqualTo(hasher.hash("01012345678"));
    }
}
