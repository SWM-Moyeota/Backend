package team.codingforest.moyeota.matching.party.infrastructure;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@DataJpaTest
@Import({PartyJpa.class, JpaAuditingConfig.class})
class PartyJpaPersistenceTest {
    private final PartyJpa parties;
    private final PartyJpaRepository repository;
    private final TestEntityManager em;

    @Autowired
    PartyJpaPersistenceTest(PartyJpa parties, PartyJpaRepository repository, TestEntityManager em) {
        this.parties = parties;
        this.repository = repository;
        this.em = em;
    }

    // ───────────────────────── 단건 조회 ─────────────────────────

    @Test
    void 단건_조회는_멤버까지_한_번에_읽는다() {
        // 상세 화면이 폴링으로 자주 부른다 - 멤버가 지연 로딩이면 조회마다 쿼리가 한 번 더 나간다
        Party saved = openAndSave();
        saved.join(2L);
        parties.save(saved);
        em.flush();
        em.clear();   // 1차 캐시를 비워야 실제 조회 쿼리가 나간다

        PartyEntity found = repository.findWithMembersById(saved.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(found.getMembers())).as("멤버가 같은 쿼리로 채워져야 한다").isTrue();
        assertThat(found.getMembers()).hasSize(2);
    }

    @Test
    void 단건_조회는_방_하나를_멤버_수와_무관하게_한_건으로_돌려준다() {
        Party saved = openAndSave();
        saved.join(2L);
        parties.save(saved);
        em.flush();
        em.clear();

        Party found = parties.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getMembers()).extracting("memberId").containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 없는_방을_단건_조회하면_비어_있다() {
        assertThat(parties.findById(999_999L)).isEmpty();
    }

    @Test
    void 정원_충족_시각은_저장_후_다시_조회해도_유지된다() {
        // 자동 종료 스윕이 이 값으로 TTL 을 판정한다 - update() 에서 빠지면 조용히 NULL 로 남아 스윕이 아무것도 안 한다
        Party saved = openAndSave();
        saved.join(2L);
        saved.join(3L);   // capacity 3 충족 → COMPLETED
        parties.save(saved);

        Party reloaded = parties.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(reloaded.getCompletedAt()).isNotNull();
    }

    @Test
    void 누가_나가면_충족_시각이_NULL로_저장된다() {
        Party saved = openAndSave();
        saved.join(2L);
        saved.join(3L);
        parties.save(saved);
        saved.leave(3L);   // COMPLETED → ACTIVE

        parties.save(saved);

        assertThat(parties.findById(saved.getId()).orElseThrow().getCompletedAt()).isNull();
    }

    @Test
    void 기준_시각_이전에_정원이_찬_방만_조회된다() {
        Instant 하루전 = Instant.now().minus(Duration.ofDays(1));
        Location 강남역 = new Location(37.4979, 127.0276);
        Location 판교역 = new Location(37.3948, 127.1112);
        Party seed = Party.open(1L, 강남역, 판교역, "강남역", "판교역", new Capacity(2), 하루전, new Radius(100), new Radius(100), 12000, 25, "_p~iF~ps|U_ulLnnqC");
        seed.join(2L);   // 멤버 목록만 빌려 쓴다
        Party 오래된방 = parties.save(Party.restore(null, 강남역, 판교역, new Radius(100), new Radius(100), "강남역", "판교역", new Capacity(2),
                seed.getMembers(), 하루전, PartyStatus.COMPLETED, 12000, 25, "_p~iF~ps|U_ulLnnqC", null, null, 하루전));
        Party 방금찬방 = openAndSave();
        방금찬방.join(2L);
        방금찬방.join(3L);
        parties.save(방금찬방);

        List<Long> targets = parties.findCompletedBefore(Instant.now().minus(Duration.ofMinutes(1)));

        assertThat(targets).contains(오래된방.getId()).doesNotContain(방금찬방.getId());
    }

    // ───────────────────────── 지도 목록(요약) 조회 ─────────────────────────

    @Test
    void 요약_조회는_멤버가_여럿이어도_방_하나를_한_건으로_돌려주고_인원수를_센다() {
        // join fetch 였을 때는 방 하나가 멤버 수만큼 행으로 불어났다 - 요약은 방당 한 건이고 인원은 DB 가 센다
        Party 세명방 = openAndSave();
        세명방.join(2L);
        parties.save(세명방);
        Party 혼자방 = parties.save(openAt(10L, 37.4980, 127.0277));

        List<PartySummary> result = parties.findSummariesWithinBounds(PartyStatus.ACTIVE, 37.49, 37.51, 127.02, 127.06, 100);

        assertThat(result).extracting(PartySummary::id, PartySummary::currentMembers)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(세명방.getId(), 2L),
                        org.assertj.core.groups.Tuple.tuple(혼자방.getId(), 1L));
    }

    @Test
    void 요약_조회는_마커에_필요한_값을_그대로_담는다() {
        Party saved = openAndSave();

        PartySummary summary = parties.findSummariesWithinBounds(PartyStatus.ACTIVE, 37.49, 37.51, 127.02, 127.06, 100).get(0);

        assertThat(summary.id()).isEqualTo(saved.getId());
        assertThat(summary.departure()).isEqualTo("강남역");
        assertThat(summary.destination()).isEqualTo("판교역");
        assertThat(summary.capacity()).isEqualTo(3);
        assertThat(summary.status()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(summary.departureLat()).isEqualTo(37.4979);
        assertThat(summary.departureLng()).isEqualTo(127.0276);
    }

    @Test
    void 요약_조회는_영역_밖이거나_상태가_다른_방을_빼고_경계는_포함한다() {
        Party 경계 = parties.save(openAt(1L, 37.49, 127.02));
        parties.save(openAt(2L, 37.5665, 126.9780));   // 시청 - 영역 밖
        Party 정원찬방 = parties.save(openAt(3L, 37.4979, 127.0276));
        정원찬방.join(4L);
        정원찬방.join(5L);   // capacity 3 충족 → COMPLETED
        parties.save(정원찬방);

        List<PartySummary> result = parties.findSummariesWithinBounds(PartyStatus.ACTIVE, 37.49, 37.51, 127.02, 127.06, 100);

        assertThat(result).extracting(PartySummary::id).containsExactly(경계.getId());
    }

    @Test
    void 요약_조회는_최신_방부터_상한까지만_돌려준다() {
        parties.save(openAt(1L, 37.4979, 127.0276));
        Party 둘째 = parties.save(openAt(2L, 37.4979, 127.0276));
        Party 셋째 = parties.save(openAt(3L, 37.4979, 127.0276));

        List<PartySummary> result = parties.findSummariesWithinBounds(PartyStatus.ACTIVE, 37.49, 37.51, 127.02, 127.06, 2);

        assertThat(result).extracting(PartySummary::id).containsExactly(셋째.getId(), 둘째.getId());
    }

    private Party openAt(Long creatorId, double lat, double lng) {
        return Party.open(creatorId, new Location(lat, lng), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
    }

    private Party openAndSave() {
        Party party = Party.open(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
        return parties.save(party);
    }

    @Test
    void 기존_방을_다시_저장해도_행이_늘어나지_않는다() {
        Party saved = openAndSave();
        long before = repository.count();

        saved.join(2L);
        parties.save(saved);

        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void 기존_방을_다시_저장하면_같은_id를_유지한다() {
        Party saved = openAndSave();

        saved.join(2L);
        Party after = parties.save(saved);

        assertThat(after.getId()).isEqualTo(saved.getId());
    }

    @Test
    void join_후_저장하면_원래_id로_조회했을_때_멤버가_반영된다() {
        Party saved = openAndSave();

        saved.join(2L);
        parties.save(saved);

        Party reloaded = parties.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMembers()).hasSize(2);
        assertThat(reloaded.hasMember(2L)).isTrue();
    }

    @Test
    void 매칭_시작_시각은_저장_후_다시_조회해도_유지된다() {
        // 스위퍼가 이 값으로 타임아웃을 판정한다 - 왕복에서 유실되면 방이 즉시 해산된다
        Instant 시작시각 = Instant.parse("2026-08-28T12:00:00Z");
        Party saved = openAndSave();
        saved.join(2L);
        saved.join(3L);   // capacity 3 충족 → COMPLETED
        saved.startMatching(시작시각);
        parties.save(saved);

        Party reloaded = parties.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getMatchingStartedAt()).isEqualTo(시작시각);
        assertThat(parties.findMatchingTargets())
                .extracting(t -> t.partyId())
                .contains(saved.getId());   // 스위퍼 조회 경로에도 잡히는지
    }

    @Test
    void 상태_변경_후_저장하면_원래_id로_조회했을_때_상태가_반영된다() {
        Party saved = openAndSave();

        saved.leave(1L);   // 혼자 남은 사람이 나가면 방이 취소된다
        parties.save(saved);

        assertThat(parties.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @Test
    void 취소된_방의_멤버는_진행_중인_방이_없는_것으로_본다() {
        Party saved = openAndSave();
        saved.join(2L);
        parties.save(saved);

        saved.leave(2L);
        saved.leave(1L);   // 마지막 멤버가 나가며 방이 취소되고, 명단도 빈다
        parties.save(saved);

        assertThat(parties.findById(saved.getId()).orElseThrow().getMembers()).isEmpty();   // 멤버 행 삭제까지 DB에 반영
        assertThat(parties.existsOngoingByMemberId(1L)).isFalse();
    }

    // ───────────────────────── 탑승 횟수 집계 ─────────────────────────

    /** 1L·2L·3L 이 탄 방을 FINISHED 까지 진행해 저장한다 */
    private void finishRide() {
        Party party = openAndSave();
        party.join(2L);
        party.join(3L);
        party.startMatching(Instant.now());
        party.assignDriver(9L);
        party.startRide(9L);
        party.completeRide(9L, 12000);
        parties.save(party);
    }

    @Test
    void 완주한_방_수를_멤버별로_센다() {
        // 인터페이스 프로젝션은 JPQL 별칭과 getter 이름이 어긋나면 실행 때만 null 이 난다 - 그래서 실제 쿼리로 검증
        finishRide();
        finishRide();
        openAndSave();                                   // ACTIVE 방 - 집계 제외

        assertThat(parties.countFinishedRides(java.util.List.of(1L, 2L, 999L)))
                .containsOnly(entry(1L, 2), entry(2L, 2));   // 999L 은 결과에 없음(0회는 빠진다)
    }

    @Test
    void 빈_목록이면_쿼리_없이_빈_결과를_돌려준다() {
        assertThat(parties.countFinishedRides(java.util.List.of())).isEmpty();
    }
}
