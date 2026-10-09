package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *  방 지문. 앱은 이 값이 달라졌는지만 보고 방 상세를 다시 읽을지 정한다.
 *  바뀌어야 할 때 안 바뀌면 낡은 화면이 남고, 안 바뀌어야 할 때 바뀌면 상세를 쓸데없이 다시 읽는다.
 */
class PartyFingerprintTest {
    private final PartyFingerprint fingerprint = new PartyFingerprint("test-secret");

    @Test
    void 같은_방_같은_상태_같은_멤버면_값이_같다() {
        assertThat(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(10L, 20L)))
                .isEqualTo(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(10L, 20L)));
    }

    @Test
    void 멤버를_읽은_순서가_달라도_값이_같다() {
        // 상세는 엔티티의 멤버 순서로, 상태 조회는 쿼리 결과 순서로 온다
        assertThat(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(20L, 10L, 30L)))
                .isEqualTo(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(10L, 30L, 20L)));
    }

    @Test
    void 인원수가_같아도_멤버가_바뀌면_값이_달라진다() {
        assertThat(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(10L, 20L)))
                .isNotEqualTo(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(10L, 30L)));
    }

    @Test
    void 멤버가_늘거나_줄면_값이_달라진다() {
        String two = fingerprint.of(1L, PartyStatus.ACTIVE, List.of(10L, 20L));

        assertThat(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(10L, 20L, 30L))).isNotEqualTo(two);
        assertThat(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(10L))).isNotEqualTo(two);
    }

    @Test
    void 상태가_바뀌면_값이_달라진다() {
        assertThat(fingerprint.of(1L, PartyStatus.COMPLETED, List.of(10L, 20L)))
                .isNotEqualTo(fingerprint.of(1L, PartyStatus.FINISHED, List.of(10L, 20L)));
    }

    @Test
    void 멤버_ID가_이어_붙어_같아_보이는_경우를_구분한다() {
        // [1, 23] 과 [12, 3] - 구분자 없이 이어 붙이면 둘 다 "123" 이 된다
        assertThat(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(1L, 23L)))
                .isNotEqualTo(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(12L, 3L)));
    }

    @Test
    void 다른_방이면_멤버가_같아도_값이_다르다() {
        assertThat(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(10L, 20L)))
                .isNotEqualTo(fingerprint.of(2L, PartyStatus.ACTIVE, List.of(10L, 20L)));
    }

    @Test
    void 멤버가_없는_방도_값이_나온다() {
        assertThat(fingerprint.of(1L, PartyStatus.CANCELED, List.of())).matches("[0-9a-f]{16}");
    }

    @Test
    void 키가_다르면_값이_다르다() {
        // 키 없이는 내부 사용자 번호를 대입해 값을 재현할 수 없어야 한다
        assertThat(new PartyFingerprint("other-secret").of(1L, PartyStatus.ACTIVE, List.of(10L, 20L)))
                .isNotEqualTo(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(10L, 20L)));
    }

    @Test
    void 값은_16진수_16글자다() {
        assertThat(fingerprint.of(1L, PartyStatus.ACTIVE, List.of(10L, 20L))).matches("[0-9a-f]{16}");
    }

    @Test
    void 키가_비어_있으면_만들_수_없다() {
        assertThatThrownBy(() -> new PartyFingerprint(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
