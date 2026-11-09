package team.codingforest.moyeota.matching.party.infrastructure;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.matching.api.dto.PartyPlaces;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingPlan;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;

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
        return parties.save(Party.create(creatorId, 강남, 판교, "강남역", "판교역", new Capacity(3), Instant.now(), 12000, 25, "encoded-path"));
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
        늦은방.join(5L, Instant.now());
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
    void 예약_목록은_정원이_찬_방을_빼고_돌려준다() {
        // 정원이 차도 ACTIVE 지만 들어갈 수 없는 방이다 - 목록에 남으면 못 타는 방을 탭하게 된다
        Instant 기준 = 뒤(Duration.ofDays(3));
        Party 정원찬방 = reserve(1L, 기준);
        정원찬방.join(2L, Instant.now());
        정원찬방.join(3L, Instant.now());   // capacity 3 충족
        parties.save(정원찬방);
        Long 빈자리방 = reserve(4L, 기준.plusSeconds(60)).getId();
        clearContext();

        assertThat(parties.findReservations(기준.minusSeconds(1), 기준.plus(Duration.ofDays(1)), 100))
                .extracting(ReservationSummary::id).containsExactly(빈자리방);
        assertThat(parties.findReservationsWithinBounds(기준.minusSeconds(1), 기준.plus(Duration.ofDays(1)), 37.0, 38.0, 126.0, 128.0, 100))
                .extracting(ReservationSummary::id).containsExactly(빈자리방);
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
    void 알림_대상은_약속_시각이_구간_안인_열린_확정_방이고_구간은_시작을_포함하고_끝을_뺀다() {
        Instant from = 뒤(Duration.ofMinutes(20)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        Instant to = from.plus(Duration.ofMinutes(1));
        Long 시작경계 = reserve(1L, from).getId();
        Long 구간안 = reserve(2L, from.plusSeconds(30)).getId();
        Long 끝경계 = reserve(3L, to).getId();
        Long 확정방 = confirmedAt(4L, from.plusSeconds(10));
        Long 미확정 = realtime(5L).getId();
        Party 닫힌방 = reserve(6L, from.plusSeconds(20));
        닫힌방.leave(6L);
        parties.save(닫힌방);
        clearContext();

        assertThat(parties.findOpenConfirmedIdsDepartingBetween(from, to))
                .contains(시작경계, 구간안, 확정방)
                .doesNotContain(끝경계, 미확정, 닫힌방.getId());
    }

    @Test
    void 출발_전_알림_대상은_같은_구간의_예약_방만이다() {
        Instant from = 뒤(Duration.ofMinutes(20)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        Instant to = from.plus(Duration.ofMinutes(1));
        Long 예약 = reserve(1L, from.plusSeconds(30)).getId();
        Long 확정한_실시간 = confirmedAt(2L, from.plusSeconds(30));
        clearContext();

        assertThat(parties.findOpenReservationIdsDepartingBetween(from, to))
                .contains(예약)
                .doesNotContain(확정한_실시간);
    }

    /** memberId 가 만들고 한 명이 더 들어와 departureAt 으로 약속을 확정한 실시간 방 */
    private Long confirmedAt(Long memberId, Instant departureAt) {
        Party party = realtime(memberId);
        party.join(memberId + 100, Instant.now());
        party.confirm(memberId, new MeetingPlan(departureAt, null, null), departureAt.minus(Duration.ofMinutes(10)));
        return parties.save(party).getId();
    }

    // ───────────────────────── 약속 시간 확정 ─────────────────────────

    @Test
    void 약속을_확정한_실시간_방의_약속_시간이_저장되고_복원된다() {
        // 저장은 이미 있는 행을 고치는 update() 경로다 - 거기서 departure_at 을 옮기지 않으면 확정이 조용히 사라진다
        Party party = realtime(1L);
        party.join(2L, Instant.now());
        parties.save(party);
        Instant 약속 = 뒤(Duration.ofMinutes(30)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        party.confirm(1L, new MeetingPlan(약속, null, null), Instant.now());
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

    // ───────────────────────── 출발지 도착 ─────────────────────────

    /** 도메인 복원이 아니라 컬럼에 실제로 들어갔는지 엔티티에서 직접 본다 */
    private PartyMemberEntity storedMember(Long partyId, Long memberId) {
        return repository.findWithMembersById(partyId).orElseThrow().getMembers().stream()
                .filter(m -> m.getMemberId().equals(memberId))
                .findFirst()
                .orElseThrow();
    }

    private static Instant micros(Instant instant) {
        return instant.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    /**
     *  1L 이 만들고 2L 이 들어온 실시간 방을 방금 지난 시각으로 확정해 저장한다 - 바로 도착·출발을 누를 수 있다.
     *  약속은 확정하는 지금부터 5분 뒤여야 하므로 "지금" 을 6분 전으로 넘겨 30초 전 약속을 만든다
     */
    private Party confirmedJustNow() {
        Party party = realtime(1L);
        party.join(2L, Instant.now());
        parties.save(party);
        party.confirm(1L, new MeetingPlan(Instant.now().minusSeconds(30), null, null), Instant.now().minus(Duration.ofMinutes(6)));
        parties.save(party);

        return party;
    }

    @Test
    void 새로_들어온_멤버_행은_도착이_비어_있다() {
        Party party = realtime(1L);
        party.join(2L, Instant.now());
        parties.save(party);
        clearContext();

        PartyMemberEntity stored = storedMember(party.getId(), 2L);

        assertThat(stored.getArrivedAt()).isNull();
        assertThat(parties.findById(party.getId()).orElseThrow().getMembers()).noneMatch(PartyMember::isArrived);
    }

    @Test
    void 처음_저장하는_방도_멤버의_도착_시각을_함께_넣는다() {
        Instant 도착 = micros(Instant.now().minusSeconds(120));
        Party party = Party.load(null, 강남, 판교, "강남역", "판교역", new Capacity(3),
                List.of(PartyMember.load(1L, Instant.now().minusSeconds(600), 도착),
                        PartyMember.load(2L, Instant.now().minusSeconds(500), null)),
                1L, Instant.now(), PartyStatus.FINISHED, 12000, 25, "encoded-path", 0L, RoomType.REALTIME, Instant.now().minusSeconds(300));
        Long id = parties.save(party).getId();
        clearContext();

        assertThat(storedMember(id, 1L).getArrivedAt()).isEqualTo(도착);
        assertThat(storedMember(id, 2L).getArrivedAt()).isNull();
    }

    @Test
    void 이미_있는_멤버의_도착이_저장되고_복원된다() {
        // 저장은 이미 있는 멤버 행을 고치는 update() 경로다 - 멤버 추가·삭제만 옮기면 도착이 조용히 사라진다
        Party party = confirmedJustNow();
        Instant 도착 = micros(Instant.now());
        // 출발지 100m 판정은 PostGIS 쿼리라 H2 로는 못 잰다 - 여기선 저장만 보므로 반경 안이라고 넘긴다
        party.arrive(2L, true, 도착);
        parties.save(party);
        clearContext();

        Party found = parties.findById(party.getId()).orElseThrow();
        PartyMember 도착한멤버 = found.getMembers().stream().filter(m -> m.getMemberId().equals(2L)).findFirst().orElseThrow();

        assertThat(storedMember(party.getId(), 2L).getArrivedAt()).isEqualTo(도착);
        assertThat(storedMember(party.getId(), 1L).getArrivedAt()).isNull();
        assertThat(도착한멤버.isArrived()).isTrue();
        assertThat(도착한멤버.getArrivedAt()).isEqualTo(도착);
        assertThat(found.getVersion()).as("참여·확정·도착").isEqualTo(3L);
    }

    @Test
    void 방장이_출발하면_FINISHED_로_저장되고_도착하지_않은_멤버의_도착은_비어_있다() {
        Party party = confirmedJustNow();
        party.arrive(1L, true, Instant.now());
        parties.save(party);
        party.depart(1L, Instant.now());
        parties.save(party);
        clearContext();

        Party found = parties.findById(party.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(storedMember(party.getId(), 1L).getArrivedAt()).isNotNull();
        assertThat(storedMember(party.getId(), 2L).getArrivedAt()).isNull();
        assertThat(found.riderIds()).as("탄 사람은 도착한 멤버뿐이다").containsExactly(1L);
    }

    // ───────────────────────── 단건 조회 ─────────────────────────

    @Test
    void 단건_조회는_멤버까지_한_번에_읽는다() {
        // 상세 화면이 폴링으로 자주 부른다 - 멤버가 지연 로딩이면 조회마다 쿼리가 한 번 더 나간다
        Party saved = createAndSave();
        saved.join(2L, Instant.now());
        parties.save(saved);
        em.flush();
        em.clear();   // 1차 캐시를 비워야 실제 조회 쿼리가 나간다

        PartyEntity found = repository.findWithMembersById(saved.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(found.getMembers())).as("멤버가 같은 쿼리로 채워져야 한다").isTrue();
        assertThat(found.getMembers()).hasSize(2);
    }

    @Test
    void 단건_조회는_방_하나를_멤버_수와_무관하게_한_건으로_돌려준다() {
        Party saved = createAndSave();
        saved.join(2L, Instant.now());
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
        Party saved = createAndSave();
        saved.join(2L, Instant.now());
        parties.save(saved);
        em.flush();
        em.clear();

        assertThat(parties.findById(saved.getId()).orElseThrow().getVersion()).isEqualTo(1);
    }

    // ───────────────────────── 지도 목록(요약) 조회 ─────────────────────────

    @Test
    void 요약_조회는_멤버가_여럿이어도_방_하나를_한_건으로_돌려주고_인원수를_센다() {
        // join fetch 였을 때는 방 하나가 멤버 수만큼 행으로 불어났다 - 요약은 방당 한 건이고 인원은 DB 가 센다
        Party 세명방 = createAndSave();
        세명방.join(2L, Instant.now());
        parties.save(세명방);
        Party 혼자방 = parties.save(createAt(10L, 37.4980, 127.0277));

        List<PartySummary> result = parties.findSummariesWithinBounds(PartyStatus.ACTIVE, 37.49, 37.51, 127.02, 127.06, 100);

        assertThat(result).extracting(PartySummary::id, PartySummary::currentMembers)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(세명방.getId(), 2L),
                        org.assertj.core.groups.Tuple.tuple(혼자방.getId(), 1L));
    }

    @Test
    void 요약_조회는_마커에_필요한_값을_그대로_담는다() {
        Party saved = createAndSave();

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
    void 요약_조회는_영역_밖이거나_정원이_찬_방을_빼고_경계는_포함한다() {
        Party 경계 = parties.save(createAt(1L, 37.49, 127.02));
        parties.save(createAt(2L, 37.5665, 126.9780));   // 시청 - 영역 밖
        Party 정원찬방 = parties.save(createAt(3L, 37.4979, 127.0276));
        정원찬방.join(4L, Instant.now());
        정원찬방.join(5L, Instant.now());   // capacity 3 충족 - ACTIVE 지만 들어갈 수 없어 목록에서 뺀다
        parties.save(정원찬방);

        List<PartySummary> result = parties.findSummariesWithinBounds(PartyStatus.ACTIVE, 37.49, 37.51, 127.02, 127.06, 100);

        assertThat(result).extracting(PartySummary::id).containsExactly(경계.getId());
    }

    @Test
    void 요약_조회는_최신_방부터_상한까지만_돌려준다() {
        parties.save(createAt(1L, 37.4979, 127.0276));
        Party 둘째 = parties.save(createAt(2L, 37.4979, 127.0276));
        Party 셋째 = parties.save(createAt(3L, 37.4979, 127.0276));

        List<PartySummary> result = parties.findSummariesWithinBounds(PartyStatus.ACTIVE, 37.49, 37.51, 127.02, 127.06, 2);

        assertThat(result).extracting(PartySummary::id).containsExactly(셋째.getId(), 둘째.getId());
    }

    private Party createAt(Long creatorId, double lat, double lng) {
        return Party.create(creatorId, new Location(lat, lng), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), Instant.now(),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
    }

    private Party createAndSave() {
        Party party = Party.create(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), Instant.now(),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
        return parties.save(party);
    }

    @Test
    void 기존_방을_다시_저장해도_행이_늘어나지_않는다() {
        Party saved = createAndSave();
        long before = repository.count();

        saved.join(2L, Instant.now());
        parties.save(saved);

        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void 기존_방을_다시_저장하면_같은_id를_유지한다() {
        Party saved = createAndSave();

        saved.join(2L, Instant.now());
        Party after = parties.save(saved);

        assertThat(after.getId()).isEqualTo(saved.getId());
    }

    @Test
    void join_후_저장하면_원래_id로_조회했을_때_멤버가_반영된다() {
        Party saved = createAndSave();

        saved.join(2L, Instant.now());
        parties.save(saved);

        Party reloaded = parties.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMembers()).hasSize(2);
        assertThat(reloaded.isMember(2L)).isTrue();
    }

    @Test
    void 상태_변경_후_저장하면_원래_id로_조회했을_때_상태가_반영된다() {
        Party saved = createAndSave();

        saved.leave(1L);   // 혼자 남은 사람이 나가면 방이 취소된다
        parties.save(saved);

        assertThat(parties.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @Test
    void 취소된_방의_멤버는_진행_중인_방이_없는_것으로_본다() {
        Party saved = createAndSave();
        saved.join(2L, Instant.now());
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
        assertThat(parties.findById(실시간).orElseThrow().getHostId()).isEqualTo(1L);
        assertThat(parties.findById(예약).orElseThrow().getHostId()).isEqualTo(2L);
    }

    @Test
    void 복원은_계산하지_않고_저장된_방장을_쓴다() {
        // 승계 규칙(가장 먼저 합류한 1L)과 다른 값을 넣어 두어야 컬럼에서 읽었는지 구분된다
        Party saved = createAndSave();
        saved.join(2L, Instant.now());
        parties.save(saved);
        em.flush();
        em.getEntityManager().createNativeQuery("UPDATE match_room SET host_id = 2 WHERE id = :id")
                .setParameter("id", saved.getId())
                .executeUpdate();
        em.clear();

        assertThat(parties.findById(saved.getId()).orElseThrow().getHostId()).isEqualTo(2L);
    }

    @Test
    void 방장이_나가면_승계된_방장이_저장된다() {
        Party saved = createAndSave();   // 1L 이 방장
        saved.join(2L, Instant.now());
        saved.join(3L, Instant.now());
        parties.save(saved);

        saved.leave(1L);
        parties.save(saved);
        clearContext();

        assertThat(storedHostId(saved.getId())).isEqualTo(2L);
        assertThat(parties.findById(saved.getId()).orElseThrow().getHostId()).isEqualTo(2L);
    }

    @Test
    void 방장이_아닌_멤버가_나가면_저장된_방장은_그대로다() {
        Party saved = createAndSave();
        saved.join(2L, Instant.now());
        parties.save(saved);

        saved.leave(2L);
        parties.save(saved);
        clearContext();

        assertThat(storedHostId(saved.getId())).isEqualTo(1L);
    }

    @Test
    void 마지막_멤버가_나가면_저장된_방장이_비워진다() {
        Party saved = createAndSave();

        saved.leave(1L);
        parties.save(saved);
        clearContext();

        assertThat(storedHostId(saved.getId())).isNull();
    }

    // ───────────────────────── 탑승 횟수 집계 ─────────────────────────

    /** 1L·2L·3L 이 든 방을 방금 지난 시각으로 확정하고, 도착한 방장(1L)이 출발을 눌러 FINISHED 로 저장한다 */
    private void finishRide() {
        Party party = createAndSave();
        party.join(2L, Instant.now());
        party.join(3L, Instant.now());
        party.confirm(1L, new MeetingPlan(Instant.now().minusSeconds(30), null, null), Instant.now().minus(Duration.ofMinutes(6)));
        party.arrive(1L, true, Instant.now());
        party.arrive(2L, true, Instant.now());
        party.depart(1L, Instant.now());
        parties.save(party);
    }

    @Test
    void 완주한_방_수를_도착한_멤버별로_센다() {
        // 인터페이스 프로젝션은 JPQL 별칭과 getter 이름이 어긋나면 실행 때만 null 이 난다 - 그래서 실제 쿼리로 검증
        finishRide();
        finishRide();
        createAndSave();                                   // ACTIVE 방 - 집계 제외

        assertThat(parties.countFinishedRides(java.util.List.of(1L, 2L, 3L, 999L)))
                .containsOnly(entry(1L, 2), entry(2L, 2));   // 3L 은 도착하지 않아 0회, 999L 은 멤버가 아님(0회는 빠진다)
    }

    @Test
    void 도착_기록이_없는_옛_FINISHED_방은_멤버_전원을_탑승으로_센다() {
        Party old = parties.save(Party.load(null, 강남, 판교, "강남역", "판교역", new Capacity(3),
                List.of(PartyMember.load(1L, Instant.now(), null), PartyMember.load(2L, Instant.now(), null)), 1L, Instant.now(), PartyStatus.FINISHED,
                9000, 20, "encoded-path", 0L, RoomType.REALTIME, null));
        clearContext();

        assertThat(parties.countFinishedRides(java.util.List.of(1L, 2L)))
                .containsOnly(entry(1L, 1), entry(2L, 1));
    }

    @Test
    void 빈_목록이면_쿼리_없이_빈_결과를_돌려준다() {
        assertThat(parties.countFinishedRides(java.util.List.of())).isEmpty();
    }

    // ───────────────────────── 출발지·도착지 이름 ─────────────────────────

    @Test
    void 방별_출발지와_도착지_이름을_한_번에_읽고_없는_방은_빠진다() {
        Long 강남방 = realtime(1L).getId();
        Long 서울방 = parties.save(Party.create(2L, 강남, 판교, "서울역", "홍대입구역", new Capacity(3), Instant.now(), 9000, 20, "encoded-path")).getId();
        clearContext();

        assertThat(parties.findPlaces(List.of(강남방, 서울방, 서울방 + 1_000)))
                .containsOnly(entry(강남방, new PartyPlaces("강남역", "판교역")), entry(서울방, new PartyPlaces("서울역", "홍대입구역")));
        assertThat(parties.findPlaces(List.of())).isEmpty();
    }
}
