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
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.domain.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    // ───────────────────────── 예약 방 ─────────────────────────

    private static final Location 강남 = new Location(37.4979, 127.0276);
    private static final Location 판교 = new Location(37.3948, 127.1112);

    private Party reserve(Long creatorId, Instant departureAt) {
        return parties.save(Party.reserve(creatorId, 강남, 판교, "강남역", "판교역", new Capacity(3), Instant.now(), departureAt));
    }

    private Party realtime(Long creatorId) {
        return parties.save(Party.open(creatorId, 강남, 판교, "강남역", "판교역", new Capacity(3), Instant.now(), Radius.DEFAULT, Radius.DEFAULT, 12000, 25, "encoded-path"));
    }

    // DB 컬럼은 마이크로초까지만 저장한다. Linux 의 Instant.now() 는 나노초까지 나와서 자르지 않으면 저장한 값과 경계 비교가 어긋난다
    private Instant 뒤(Duration duration) {
        return Instant.now().truncatedTo(ChronoUnit.MICROS).plus(duration);
    }

    private void clearContext() {
        em.flush();
        em.clear();
    }

    @Test
    void 예약_방을_저장하고_복원한다() {
        Instant 출발 = 뒤(Duration.ofDays(1)).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Long id = reserve(1L, 출발).getId();
        clearContext();

        Party found = parties.findById(id).orElseThrow();

        assertThat(found.getRoomType()).isEqualTo(RoomType.RESERVED);
        assertThat(found.getDepartureAt()).isEqualTo(출발);
        assertThat(found.getRoute()).isNull();
        assertThat(found.getEstimatedFare()).isNull();
        assertThat(found.getEstimatedTime()).isNull();
    }

    @Test
    void 알림_시각이_저장된다() {
        Party party = reserve(1L, 뒤(Duration.ofDays(1)));
        Instant 보낸시각 = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        party.markReminderSent(보낸시각);
        parties.save(party);
        clearContext();

        assertThat(parties.findById(party.getId()).orElseThrow().getReminderSentAt()).isEqualTo(보낸시각);
    }

    @Test
    void 실시간_목록은_예약_방을_빼고_돌려준다() {
        Long 실시간 = realtime(1L).getId();
        Long 예약 = reserve(2L, 뒤(Duration.ofDays(1))).getId();
        clearContext();

        assertThat(parties.findRealtimeByStatus(PartyStatus.ACTIVE)).extracting(Party::getId).contains(실시간).doesNotContain(예약);
        assertThat(parties.findSummariesWithinBounds(PartyStatus.ACTIVE, 37.0, 38.0, 126.0, 128.0, 100))
                .extracting(PartySummary::id).contains(실시간).doesNotContain(예약);
    }

    @Test
    void 진행_중인_실시간_방이_있는지_종류를_구분해_확인한다() {
        realtime(1L);
        reserve(2L, 뒤(Duration.ofDays(1)));
        clearContext();

        assertThat(parties.existsOngoingRealtimeByMemberId(1L)).isTrue();
        assertThat(parties.existsOngoingRealtimeByMemberId(2L)).isFalse();
    }

    @Test
    void 끝난_실시간_방은_진행_중으로_치지_않는다() {
        Party party = realtime(1L);
        party.leave(1L);
        parties.save(party);
        clearContext();

        assertThat(parties.existsOngoingRealtimeByMemberId(1L)).isFalse();
    }

    @Test
    void 기간_안의_진행_중인_예약_방만_겹치는_것으로_본다() {
        Instant 출발 = 뒤(Duration.ofDays(1));
        reserve(1L, 출발);
        clearContext();

        assertThat(parties.existsOngoingReservationBetween(1L, 출발.minusSeconds(1), 출발.plusSeconds(1))).isTrue();
        assertThat(parties.existsOngoingReservationBetween(1L, 출발, 출발)).isTrue();
        assertThat(parties.existsOngoingReservationBetween(1L, 출발.plusSeconds(1), 출발.plusSeconds(2))).isFalse();
        assertThat(parties.existsOngoingReservationBetween(2L, 출발.minusSeconds(1), 출발.plusSeconds(1))).isFalse();
    }

    @Test
    void 취소된_예약_방은_겹치는_것으로_보지_않는다() {
        Instant 출발 = 뒤(Duration.ofDays(1));
        Party party = reserve(1L, 출발);
        party.expireRecruiting();
        parties.save(party);
        clearContext();

        assertThat(parties.existsOngoingReservationBetween(1L, 출발.minusSeconds(1), 출발.plusSeconds(1))).isFalse();
    }

    @Test
    void 예약_목록은_기간_안의_ACTIVE_방을_출발_순으로_인원과_함께_돌려준다() {
        Instant 기준 = 뒤(Duration.ofDays(3));
        Party 늦은방 = reserve(1L, 기준.plusSeconds(600));
        늦은방.join(5L);
        parties.save(늦은방);
        Long 이른방 = reserve(2L, 기준).getId();
        reserve(3L, 기준.plus(Duration.ofDays(2)));
        Party 취소방 = reserve(4L, 기준.plusSeconds(60));
        취소방.expireRecruiting();
        parties.save(취소방);
        clearContext();

        List<ReservationSummary> found = parties.findReservations(기준.minusSeconds(1), 기준.plus(Duration.ofDays(1)), 100);

        assertThat(found).extracting(ReservationSummary::id).containsExactly(이른방, 늦은방.getId());
        assertThat(found.get(1).currentMembers()).isEqualTo(2L);
    }

    @Test
    void 예약_목록은_상한만큼만_돌려준다() {
        Instant 기준 = 뒤(Duration.ofDays(3));
        Long 이른방 = reserve(1L, 기준).getId();
        reserve(2L, 기준.plusSeconds(60));
        clearContext();

        assertThat(parties.findReservations(기준.minusSeconds(1), 기준.plus(Duration.ofDays(1)), 1))
                .extracting(ReservationSummary::id).containsExactly(이른방);
    }

    @Test
    void 영역_예약_목록은_출발지가_영역_안인_방만_돌려준다() {
        Instant 기준 = 뒤(Duration.ofDays(3));
        Long 강남방 = reserve(1L, 기준).getId();
        clearContext();

        assertThat(parties.findReservationsWithinBounds(기준.minusSeconds(1), 기준.plusSeconds(1), 37.0, 38.0, 126.0, 128.0, 100))
                .extracting(ReservationSummary::id).contains(강남방);
        assertThat(parties.findReservationsWithinBounds(기준.minusSeconds(1), 기준.plusSeconds(1), 36.0, 36.5, 126.0, 128.0, 100)).isEmpty();
    }

    @Test
    void 오래된_실시간_방과_출발_시각이_지난_예약_방이_모집_만료_대상이다() {
        Long 새실시간 = realtime(1L).getId();
        Long 미래예약 = reserve(2L, 뒤(Duration.ofHours(1))).getId();
        Instant 나중 = 뒤(Duration.ofMinutes(30));
        clearContext();

        assertThat(parties.findRecruitingExpired(Instant.now().minus(Duration.ofMinutes(15)), Instant.now()))
                .doesNotContain(새실시간, 미래예약);
        assertThat(parties.findRecruitingExpired(뒤(Duration.ofSeconds(1)), 나중)).contains(새실시간).doesNotContain(미래예약);
        assertThat(parties.findRecruitingExpired(Instant.now().minus(Duration.ofMinutes(15)), 뒤(Duration.ofHours(2)))).contains(미래예약).doesNotContain(새실시간);
    }

    @Test
    void 정원이_찬_예약_방은_완료_시각이_아니라_출발_시각으로_정리_대상을_가른다() {
        Party 내일방 = parties.save(Party.reserve(1L, 강남, 판교, "강남역", "판교역", new Capacity(2), Instant.now(), 뒤(Duration.ofDays(1))));
        내일방.join(2L);
        parties.save(내일방);
        clearContext();

        assertThat(parties.findCompletedBefore(뒤(Duration.ofHours(1)))).doesNotContain(내일방.getId());
        assertThat(parties.findCompletedBefore(뒤(Duration.ofDays(2)))).contains(내일방.getId());
    }

    @Test
    void 알림_대상은_보내지_않은_기간_안의_예약_방이다() {
        Instant 기준 = 뒤(Duration.ofMinutes(20));
        Long 대상 = reserve(1L, 기준).getId();
        Party 보낸방 = reserve(2L, 기준);
        보낸방.markReminderSent(Instant.now());
        parties.save(보낸방);
        Long 먼방 = reserve(3L, 뒤(Duration.ofDays(1))).getId();
        Long 실시간 = realtime(4L).getId();
        clearContext();

        assertThat(parties.findReservationsToRemind(Instant.now(), 뒤(Duration.ofMinutes(30))))
                .contains(대상).doesNotContain(보낸방.getId(), 먼방, 실시간);
    }

    // ───────────────────────── 약속 시간 확정 ─────────────────────────

    @Test
    void 약속을_확정한_실시간_방의_약속_시간이_저장되고_복원된다() {
        // 저장은 이미 있는 행을 고치는 update() 경로다 - 거기서 departure_at 을 옮기지 않으면 확정이 조용히 사라진다
        Party party = realtime(1L);
        party.join(2L);
        parties.save(party);
        Instant 약속 = 뒤(Duration.ofMinutes(30)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        party.confirm(1L, 약속, Instant.now());
        parties.save(party);
        clearContext();

        Party found = parties.findById(party.getId()).orElseThrow();

        assertThat(repository.findById(party.getId()).orElseThrow().getDepartureAt()).isEqualTo(약속);
        assertThat(found.getRoomType()).isEqualTo(RoomType.REALTIME);
        assertThat(found.getDepartureAt()).isEqualTo(약속);
        assertThat(found.isConfirmed()).isTrue();
        assertThat(found.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(found.getRoute()).as("실시간 방의 경로·요금은 그대로").isEqualTo("encoded-path");
    }

    @Test
    void 확정_전_실시간_방은_약속_시간이_비어_있다() {
        Long id = realtime(1L).getId();
        clearContext();

        Party found = parties.findById(id).orElseThrow();

        assertThat(found.getDepartureAt()).isNull();
        assertThat(found.isConfirmed()).isFalse();
    }

    @Test
    void 약속을_확정한_실시간_방은_정원_충족_시각이_아니라_약속_시간으로_자동_종료_대상을_가른다() {
        // 확정 순간이 정원 충족 시각이 되지만 약속은 최대 1시간 뒤다 - 정원 충족 시각으로 가르면 만나기 전에 방이 닫힌다
        Party party = realtime(1L);
        party.join(2L);
        Instant 약속 = 뒤(Duration.ofMinutes(50));
        party.confirm(1L, 약속, Instant.now());
        parties.save(party);
        clearContext();

        assertThat(parties.findCompletedBefore(뒤(Duration.ofMinutes(40)))).doesNotContain(party.getId());
        assertThat(parties.findCompletedBefore(약속.plusSeconds(1))).contains(party.getId());
    }

    @Test
    void 확정_전_실시간_방은_여전히_정원_충족_시각으로_자동_종료_대상을_가른다() {
        Party party = parties.save(Party.open(1L, 강남, 판교, "강남역", "판교역", new Capacity(2), Instant.now(), Radius.DEFAULT, Radius.DEFAULT, 12000, 25, "encoded-path"));
        party.join(2L);   // capacity 2 충족 → COMPLETED, 약속 시간은 없다
        parties.save(party);
        clearContext();

        assertThat(parties.findCompletedBefore(Instant.now().minusSeconds(60))).doesNotContain(party.getId());
        assertThat(parties.findCompletedBefore(뒤(Duration.ofSeconds(1)))).contains(party.getId());
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
    void 멤버만_바뀐_참여도_version_이_저장된다() {
        Party saved = openAndSave();
        saved.join(2L);
        parties.save(saved);
        em.flush();
        em.clear();

        assertThat(parties.findById(saved.getId()).orElseThrow().getVersion()).isEqualTo(1);
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
        Party 오래된방 = parties.save(Party.load(null, 강남역, 판교역, new Radius(100), new Radius(100), "강남역", "판교역", new Capacity(2),
                seed.getMembers(), 하루전, PartyStatus.COMPLETED, 12000, 25, "_p~iF~ps|U_ulLnnqC", null, null, 하루전, 0L));
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

    // ───────────────────────── 방장 ─────────────────────────

    /** 도메인 복원은 비거나 어긋난 host_id 를 승계 규칙으로 메워 준다 - 컬럼에 실제로 저장됐는지는 엔티티에서 직접 본다 */
    private Long storedHostId(Long partyId) {
        return repository.findById(partyId).orElseThrow().getHostId();
    }

    @Test
    void 방을_만든_사람이_방장으로_저장되고_복원된다() {
        Long 실시간 = realtime(1L).getId();
        Long 예약 = reserve(2L, 뒤(Duration.ofDays(1))).getId();
        clearContext();

        assertThat(storedHostId(실시간)).isEqualTo(1L);
        assertThat(storedHostId(예약)).isEqualTo(2L);
        assertThat(parties.findById(실시간).orElseThrow().hostId()).isEqualTo(1L);
        assertThat(parties.findById(예약).orElseThrow().hostId()).isEqualTo(2L);
    }

    @Test
    void 복원은_계산하지_않고_저장된_방장을_쓴다() {
        // 승계 규칙(가장 먼저 합류한 1L)과 다른 값을 넣어 두어야 컬럼에서 읽었는지 구분된다
        Party saved = openAndSave();
        saved.join(2L);
        parties.save(saved);
        em.flush();
        em.getEntityManager().createNativeQuery("UPDATE match_room SET host_id = 2 WHERE id = :id")
                .setParameter("id", saved.getId())
                .executeUpdate();
        em.clear();

        assertThat(parties.findById(saved.getId()).orElseThrow().hostId()).isEqualTo(2L);
    }

    @Test
    void 방장이_나가면_승계된_방장이_저장된다() {
        Party saved = openAndSave();   // 1L 이 방장
        saved.join(2L);
        saved.join(3L);
        parties.save(saved);

        saved.leave(1L);
        parties.save(saved);
        clearContext();

        assertThat(storedHostId(saved.getId())).isEqualTo(2L);
        assertThat(parties.findById(saved.getId()).orElseThrow().hostId()).isEqualTo(2L);
    }

    @Test
    void 방장이_아닌_멤버가_나가면_저장된_방장은_그대로다() {
        Party saved = openAndSave();
        saved.join(2L);
        parties.save(saved);

        saved.leave(2L);
        parties.save(saved);
        clearContext();

        assertThat(storedHostId(saved.getId())).isEqualTo(1L);
    }

    @Test
    void 마지막_멤버가_나가면_저장된_방장이_비워진다() {
        Party saved = openAndSave();

        saved.leave(1L);
        parties.save(saved);
        clearContext();

        assertThat(storedHostId(saved.getId())).isNull();
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
