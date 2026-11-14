package team.codingforest.moyeota.user.social.infrastructure;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.user.social.domain.SocialUser;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(SocialUserJpa.class)
class SocialUserJpaPersistenceTest {
    private final SocialUserJpa socialUsers;
    private final EntityManager em;

    @Autowired
    SocialUserJpaPersistenceTest(SocialUserJpa socialUsers, EntityManager em) {
        this.socialUsers = socialUsers;
        this.em = em;
    }

    @Test
    void 같은_회원은_구글과_애플을_동시에_연결한다() {
        socialUsers.register(9L, SocialType.GOOGLE, "google-multi");
        socialUsers.register(9L, SocialType.APPLE, "apple-multi");
        em.flush(); em.clear();
        assertThat(socialUsers.find(SocialType.GOOGLE,"google-multi").orElseThrow().getUserId()).isEqualTo(9L);
        assertThat(socialUsers.find(SocialType.APPLE,"apple-multi").orElseThrow().getUserId()).isEqualTo(9L);
        socialUsers.deleteByUserId(9L); em.flush();
        assertThat(socialUsers.find(SocialType.APPLE,"apple-multi")).isEmpty();
        assertThat(socialUsers.find(SocialType.GOOGLE,"google-multi")).isEmpty();
    }

    @Test
    void 같은_회원의_같은_공급자_중복은_DB에서_막는다() {
        socialUsers.register(9L, SocialType.GOOGLE, "google-first"); em.flush();
        assertThatThrownBy(() -> { socialUsers.register(9L,SocialType.GOOGLE,"google-second");em.flush(); })
                .isInstanceOfAny(DataIntegrityViolationException.class,jakarta.persistence.PersistenceException.class);
    }

    @Test
    void 소셜_종류와_소셜_아이디로_회원을_찾는다() {
        socialUsers.register(1L, SocialType.GOOGLE, "google-sub-1");
        em.flush();
        em.clear();

        SocialUser found = socialUsers.find(SocialType.GOOGLE, "google-sub-1").orElseThrow();

        assertThat(found.getUserId()).isEqualTo(1L);
        assertThat(found.getSocialCi()).as("구글은 CI 를 주지 않는다").isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(socialUsers.exists(SocialType.GOOGLE, "google-sub-1")).isTrue();
    }

    @Test
    void 소셜_아이디가_같아도_소셜_종류가_다르면_다른_계정이다() {
        socialUsers.register(1L, SocialType.GOOGLE, "same-id");
        em.flush();

        assertThat(socialUsers.find(SocialType.KAKAO, "same-id")).isEmpty();
        assertThat(socialUsers.exists(SocialType.KAKAO, "same-id")).isFalse();

        socialUsers.register(2L, SocialType.KAKAO, "same-id");
        em.flush();

        assertThat(socialUsers.find(SocialType.KAKAO, "same-id").orElseThrow().getUserId()).isEqualTo(2L);
    }

    @Test
    void 같은_소셜_계정은_두_회원에_연결할_수_없다() {
        socialUsers.register(1L, SocialType.GOOGLE, "google-sub-1");
        em.flush();

        assertThatThrownBy(() -> {
            socialUsers.register(2L, SocialType.GOOGLE, "google-sub-1");
            em.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
    }

    @Test
    void 회원_번호로_지우면_다시_찾을_수_없다() {
        socialUsers.register(1L, SocialType.GOOGLE, "google-sub-1");
        em.flush();

        socialUsers.deleteByUserId(1L);
        em.flush();

        assertThat(socialUsers.find(SocialType.GOOGLE, "google-sub-1")).isEmpty();
    }

    @Test
    void 없는_회원_번호를_지워도_오류가_나지_않는다() {
        // 로컬 회원 탈퇴에서도 같이 불린다
        socialUsers.deleteByUserId(999L);
        em.flush();

        assertThat(socialUsers.findByUserIdForUpdate(999L)).isEmpty();
    }
}
