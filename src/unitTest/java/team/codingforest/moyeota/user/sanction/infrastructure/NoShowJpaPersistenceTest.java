package team.codingforest.moyeota.user.sanction.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.user.sanction.domain.NoShow;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest
@Import({NoShowJpa.class, JpaAuditingConfig.class})
class NoShowJpaPersistenceTest {
    private static final Long USER_ID = 1L;
    private static final Long PARTY_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-11-03T09:00:00Z");
    private static final Instant MEETING_AT = NOW.minus(Duration.ofHours(1));

    private final NoShowJpa noShows;
    private final NoShowRepository repository;
    private final TestEntityManager em;

    @Autowired
    NoShowJpaPersistenceTest(NoShowJpa noShows, NoShowRepository repository, TestEntityManager em) {
        this.noShows = noShows;
        this.repository = repository;
        this.em = em;
    }

    @DisplayName("add - 처음이면 더하고 생성 시각을 찍는다")
    @Test
    void add() {

        /*
         * test
         */
        boolean added = noShows.add(USER_ID, PARTY_ID, MEETING_AT);
        clear();

        /*
         * verify
         */
        assertThat(added).isTrue();
        NoShowEntity saved = repository.findByUserIdAndPartyId(USER_ID, PARTY_ID).orElseThrow();
        assertThat(saved.getMeetingAt()).isEqualTo(MEETING_AT);
        assertThat(saved.getVoidedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @DisplayName("add - 같은 사용자·방이 이미 있으면(무효로 돌아간 것 포함) 더하지 않고 false")
    @Test
    void add_duplicate() {

        /*
         * settings
         */
        noShows.add(USER_ID, PARTY_ID, MEETING_AT);
        noShows.add(USER_ID, PARTY_ID + 1, MEETING_AT);
        noShows.markVoided(USER_ID, PARTY_ID + 1, NOW);
        clear();

        /*
         * test
         */
        boolean again = noShows.add(USER_ID, PARTY_ID, MEETING_AT.plusSeconds(1));
        boolean againVoided = noShows.add(USER_ID, PARTY_ID + 1, MEETING_AT);
        clear();

        /*
         * verify
         */
        assertThat(again).isFalse();
        assertThat(againVoided).isFalse();
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByUserIdAndPartyId(USER_ID, PARTY_ID).orElseThrow().getMeetingAt()).isEqualTo(MEETING_AT);
    }

    @DisplayName("add - 다른 사용자의 같은 방, 같은 사용자의 다른 방은 따로 더한다")
    @Test
    void add_otherUserOrParty() {

        /*
         * test
         */
        boolean first = noShows.add(USER_ID, PARTY_ID, MEETING_AT);
        boolean otherUser = noShows.add(USER_ID + 1, PARTY_ID, MEETING_AT);
        boolean otherParty = noShows.add(USER_ID, PARTY_ID + 1, MEETING_AT);

        /*
         * verify
         */
        assertThat(List.of(first, otherUser, otherParty)).containsOnly(true);
        assertThat(repository.count()).isEqualTo(3);
    }

    @DisplayName("saveAndFlush - add 의 확인을 건너뛰고 같은 사용자·방을 저장하면 유니크 제약이 막는다")
    @Test
    void uniqueConstraint() {

        /*
         * settings
         */
        noShows.add(USER_ID, PARTY_ID, MEETING_AT);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> repository.saveAndFlush(NoShowEntity.of(USER_ID, PARTY_ID, MEETING_AT)));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("markVoided - 무효 시각을 남기고 true, 다시 부르면 처음 시각을 두고 false")
    @Test
    void markVoided() {

        /*
         * settings
         */
        noShows.add(USER_ID, PARTY_ID, MEETING_AT);
        clear();

        /*
         * test
         */
        boolean first = noShows.markVoided(USER_ID, PARTY_ID, NOW);
        clear();
        boolean second = noShows.markVoided(USER_ID, PARTY_ID, NOW.plusSeconds(60));
        clear();

        /*
         * verify
         */
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(repository.findByUserIdAndPartyId(USER_ID, PARTY_ID).orElseThrow().getVoidedAt()).isEqualTo(NOW);
    }

    @DisplayName("markVoided - 기록이 없으면 아무것도 하지 않고 false")
    @Test
    void markVoided_notFound() {

        /*
         * test
         */
        boolean voided = noShows.markVoided(USER_ID, PARTY_ID, NOW);

        /*
         * verify
         */
        assertThat(voided).isFalse();
        assertThat(repository.count()).isZero();
    }

    @DisplayName("findUnexpired - 약속 시간이 지금 - 30일보다 뒤인 그 사용자의 노쇼만, 무효로 돌린 것도 함께 준다")
    @Test
    void findUnexpired() {

        /*
         * settings
         */
        Instant almost = NOW.minus(Duration.ofDays(30)).plusSeconds(1);
        noShows.add(USER_ID, 1L, almost);
        noShows.add(USER_ID, 2L, NOW.minus(Duration.ofDays(30)));
        noShows.add(USER_ID, 3L, NOW.minus(Duration.ofDays(31)));
        noShows.add(USER_ID, 4L, MEETING_AT);
        noShows.markVoided(USER_ID, 4L, NOW);
        noShows.add(USER_ID + 1, 5L, MEETING_AT);
        clear();

        /*
         * test
         */
        List<NoShow> found = noShows.findUnexpired(USER_ID, NOW);

        /*
         * verify
         */
        assertThat(found).containsExactlyInAnyOrder(new NoShow(almost, null), new NoShow(MEETING_AT, NOW));
    }

    private void clear() {
        em.flush();
        em.clear();
    }
}
