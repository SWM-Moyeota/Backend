package team.codingforest.moyeota.user.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.enums.LoginType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

@DataJpaTest
@Import({UserJpa.class, JpaAuditingConfig.class})
class UserJpaPersistenceTest {
    private final UserJpa users;
    private final UserRepository repository;

    @Autowired
    UserJpaPersistenceTest(UserJpa users, UserRepository repository) {
        this.users = users;
        this.repository = repository;
    }

    private User saveNew() {
        return users.save(User.from(UUID.randomUUID(), LoginType.LOCAL));
    }

    @Test
    void 기존_유저를_다시_저장해도_행이_늘어나지_않는다() {
        // 종전 save 는 id 없이 새 엔티티를 만들어 매번 insert 했다 - publicId unique 위반으로 터지던 경로
        User saved = saveNew();
        long before = repository.count();

        saved.registerFcmToken("token-abc");
        users.save(saved);

        assertThat(repository.count()).isEqualTo(before);
        assertThat(users.findById(saved.getId()).getFcmToken()).isEqualTo("token-abc");
    }

    @Test
    void 토큰이_등록된_유저만_조회된다() {
        User 등록 = saveNew();
        User 미등록 = saveNew();
        등록.registerFcmToken("token-abc");
        users.save(등록);

        assertThat(users.findFcmTokens(List.of(등록.getId(), 미등록.getId(), 999L)))
                .containsExactly(entry(등록.getId(), "token-abc"));
    }

    @Test
    void 빈_목록이면_쿼리_없이_빈_결과를_돌려준다() {
        assertThat(users.findFcmTokens(List.of())).isEmpty();
    }

    // ───────────────────────── 닉네임 unique ─────────────────────────

    @Test
    void 같은_닉네임은_두_번_저장할_수_없다() {
        users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "길동이"));

        assertThatThrownBy(() -> {
            users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "길동이"));
            repository.flush();
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void 닉네임이_없는_소셜_가입자는_여럿이어도_충돌하지_않는다() {
        // unique 컬럼이라도 null 끼리는 충돌하지 않아야 소셜 가입이 막히지 않는다
        users.save(User.from(UUID.randomUUID(), LoginType.SOCIAL));
        users.save(User.from(UUID.randomUUID(), LoginType.SOCIAL));
        repository.flush();

        assertThat(users.existsByNickname("아무거나")).isFalse();
    }
}
