package team.codingforest.moyeota.user.profile.infrastructure;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.user.common.crypto.FieldHasher;
import team.codingforest.moyeota.user.common.domain.enums.Gender;
import team.codingforest.moyeota.user.profile.domain.UserProfile;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({UserProfileJpa.class, FieldHasher.class})
class UserProfileJpaPersistenceTest {
    private final UserProfileJpa profiles;
    private final EntityManager em;

    @Autowired
    UserProfileJpaPersistenceTest(UserProfileJpa profiles, EntityManager em) {
        this.profiles = profiles;
        this.em = em;
    }

    private UserProfile 프로필(Long userId, String phoneNumber) {
        return UserProfile.of(userId, "홍길동", Instant.parse("2000-01-01T00:00:00Z"), phoneNumber, Gender.MALE, "hong@example.com");
    }

    @Test
    void 이름_이메일_전화번호는_암호문으로_저장된다() {
        profiles.save(프로필(1L, "010-1234-5678"));
        em.flush();

        Object[] row = (Object[]) em.createNativeQuery("SELECT name, email, phone_number FROM user_profile WHERE id = 1")
                .getSingleResult();

        assertThat(row).allSatisfy(column -> assertThat((String) column).startsWith("v1:"));
    }

    @Test
    void 다시_읽으면_평문으로_돌아온다() {
        profiles.save(프로필(1L, "010-1234-5678"));
        em.flush();
        em.clear();   // 1차 캐시가 아니라 DB 에서 읽어 복호화를 거치게 한다

        UserProfile found = profiles.findByUserId(1L).orElseThrow();

        assertThat(found.getName()).isEqualTo("홍길동");
        assertThat(found.getEmail()).isEqualTo("hong@example.com");
        assertThat(found.getPhoneNumber()).isEqualTo("01012345678");
    }

    @Test
    void 암호화된_전화번호도_가입_여부를_찾을_수_있다() {
        profiles.save(프로필(1L, "010-1234-5678"));
        em.flush();

        assertThat(profiles.existsByPhoneNumber("01012345678")).isTrue();
        assertThat(profiles.existsByPhoneNumber("01099998888")).isFalse();
    }

    @Test
    void 같은_전화번호는_두_번_저장할_수_없다() {
        profiles.save(프로필(1L, "010-1234-5678"));
        em.flush();

        // 암호문은 매번 달라 phone_number 로는 못 막는다 - phone_hash unique 가 막는다
        assertThatThrownBy(() -> {
            profiles.save(프로필(2L, "010-1234-5678"));
            em.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class, org.hibernate.exception.ConstraintViolationException.class);
    }
}
