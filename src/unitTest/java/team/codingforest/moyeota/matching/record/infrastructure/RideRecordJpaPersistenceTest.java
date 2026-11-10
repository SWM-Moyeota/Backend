package team.codingforest.moyeota.matching.record.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;
import team.codingforest.moyeota.matching.party.infrastructure.PartyJpa;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({RideRecordJpa.class, PartyJpa.class, JpaAuditingConfig.class})
class RideRecordJpaPersistenceTest {
    private static final Long 나 = 100L;
    private static final Long 동승자 = 200L;
    private static final Long 남 = 300L;
    private static final Instant 기준 = Instant.parse("2026-10-01T10:00:00.123456Z");

    private final RideRecordJpa rideRecords;
    private final RideRecordJpaRepository repository;
    private final PartyJpa parties;
    private final TestEntityManager em;

    @Autowired
    RideRecordJpaPersistenceTest(RideRecordJpa rideRecords, RideRecordJpaRepository repository, PartyJpa parties, TestEntityManager em) {
        this.rideRecords = rideRecords;
        this.repository = repository;
        this.parties = parties;
        this.em = em;
    }

    @Test
    void 끝난_동승_기록은_출발지_도착지_승차_시각으로_저장되고_목록에_나온다() {
        Long partyId = party(나, 동승자);

        rideRecords.save(RideRecord.create(partyId, "강남역", "판교역", 기준));
        clear();

        RideRecord found = rideRecords.findById(partyId).orElseThrow();
        assertThat(found.getDeparturePlace()).isEqualTo("강남역");
        assertThat(found.getDestinationPlace()).isEqualTo("판교역");
        assertThat(found.getPickupTime()).isEqualTo(기준);
        assertThat(rideRecords.findByMember(나, 10)).extracting(RideRecord::getMatchId).containsExactly(partyId);
    }

    @Test
    void 처음_저장하면_생성_시각이_찍히고_같은_방을_다시_저장해도_행이_늘지_않고_생성_시각은_그대로다() {
        // id 를 직접 넣는 엔티티라 save 는 merge(SELECT 뒤 INSERT 또는 UPDATE)로 돈다 - 같은 match_id 를 다시 저장해도 PK 위반 없이 덮어쓴다
        Long partyId = party(나);
        rideRecords.save(RideRecord.create(partyId, "강남역", "판교역", 기준));
        clear();
        Instant createdAt = repository.findById(partyId).orElseThrow().getCreatedAt();

        RideRecord 다시 = RideRecord.create(partyId, "강남역", "판교역", 기준.plusSeconds(60));
        rideRecords.save(다시);
        clear();

        RideRecordEntity found = repository.findById(partyId).orElseThrow();
        assertThat(createdAt).isNotNull();
        assertThat(found.getCreatedAt()).isEqualTo(createdAt);
        assertThat(found.getUpdatedAt()).isNotNull().isAfterOrEqualTo(createdAt);
        assertThat(found.getPickupTime()).isEqualTo(다시.getPickupTime());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void 없는_기록은_비어_있다() {
        assertThat(rideRecords.findById(999_999L)).isEmpty();
    }

    @Test
    void 첫_페이지는_승차_최신순으로_limit_까지만_준다() {
        saveRide(party(나), 기준.plusSeconds(10));
        Long 둘째 = saveRide(party(나, 동승자), 기준.plusSeconds(20));
        Long 셋째 = saveRide(party(동승자, 나), 기준.plusSeconds(30));
        clear();

        List<RideRecord> result = rideRecords.findByMember(나, 2);

        assertThat(result).extracting(RideRecord::getMatchId).containsExactly(셋째, 둘째);
    }

    @Test
    void 승차_시각이_같으면_match_id_큰_순이고_커서가_경계에서_빠짐없이_이어진다() {
        Instant 같은시각 = 기준.plusSeconds(20);
        Long 이전 = saveRide(party(나), 기준.plusSeconds(10));
        Long 동률1 = saveRide(party(나), 같은시각);
        Long 동률2 = saveRide(party(나), 같은시각);
        Long 동률3 = saveRide(party(나), 같은시각);
        Long 이후 = saveRide(party(나), 기준.plusSeconds(30));
        clear();

        List<RideRecord> 첫페이지 = rideRecords.findByMember(나, 2);
        RideRecord 첫끝 = 첫페이지.getLast();
        List<RideRecord> 둘째페이지 = rideRecords.findByMemberBefore(나, 첫끝.getPickupTime(), 첫끝.getMatchId(), 2);
        RideRecord 둘끝 = 둘째페이지.getLast();
        List<RideRecord> 셋째페이지 = rideRecords.findByMemberBefore(나, 둘끝.getPickupTime(), 둘끝.getMatchId(), 2);

        assertThat(첫페이지).extracting(RideRecord::getMatchId).containsExactly(이후, 동률3);
        assertThat(둘째페이지).extracting(RideRecord::getMatchId).containsExactly(동률2, 동률1);
        assertThat(셋째페이지).extracting(RideRecord::getMatchId).containsExactly(이전);
        assertThat(rideRecords.findByMember(나, 10)).extracting(RideRecord::getMatchId)
                .containsExactly(이후, 동률3, 동률2, 동률1, 이전);
        assertThat(rideRecords.findByMemberBefore(나, 기준.plusSeconds(30), 이후, 10)).extracting(RideRecord::getMatchId)
                .containsExactly(동률3, 동률2, 동률1, 이전);
    }

    @Test
    void 먼저_만든_방이_나중에_끝나도_커서로_한_번씩_빠짐없이_넘어간다() {
        Long 가장늦게탐 = saveRide(party(나), 기준.plusSeconds(30));
        Long 가장일찍탐 = saveRide(party(나), 기준.plusSeconds(10));
        Long 중간에탐 = saveRide(party(나), 기준.plusSeconds(20));
        clear();

        List<Long> seen = new ArrayList<>();
        List<RideRecord> page = rideRecords.findByMember(나, 1);
        while(!page.isEmpty() && seen.size() < 10) {
            RideRecord last = page.getLast();
            seen.add(last.getMatchId());
            page = rideRecords.findByMemberBefore(나, last.getPickupTime(), last.getMatchId(), 1);
        }

        assertThat(seen).containsExactly(가장늦게탐, 중간에탐, 가장일찍탐);
    }

    @Test
    void 내가_멤버가_아닌_방은_나오지_않는다() {
        Long 내방 = saveRide(party(나, 동승자), 기준.plusSeconds(10));
        saveRide(party(동승자, 남), 기준.plusSeconds(20));
        clear();

        assertThat(rideRecords.findByMember(나, 10)).extracting(RideRecord::getMatchId).containsExactly(내방);
        assertThat(rideRecords.findByMemberBefore(나, 기준.plusSeconds(60), Long.MAX_VALUE, 10))
                .extracting(RideRecord::getMatchId).containsExactly(내방);
    }

    @Test
    void 기록이_없는_방은_나오지_않는다() {
        Long 끝난방 = saveRide(party(나), 기준.plusSeconds(10));
        party(나);
        clear();

        assertThat(rideRecords.findByMember(나, 10)).extracting(RideRecord::getMatchId).containsExactly(끝난방);
        assertThat(rideRecords.findByMemberBefore(나, 기준.plusSeconds(60), Long.MAX_VALUE, 10))
                .extracting(RideRecord::getMatchId).containsExactly(끝난방);
    }

    @Test
    void 커서가_마지막이면_빈_목록이고_기록이_없는_사람도_빈_목록이다() {
        Long 유일 = saveRide(party(나), 기준.plusSeconds(10));
        clear();

        assertThat(rideRecords.findByMemberBefore(나, 기준.plusSeconds(10), 유일, 10)).isEmpty();
        assertThat(rideRecords.findByMember(남, 10)).isEmpty();
    }

    @Test
    void 방별_멤버_id_는_합류_순으로_모이고_id_순서와_달라도_합류_순을_따른다() {
        Long 셋방 = savedParty(List.of(PartyMember.load(동승자, 기준.plusSeconds(2), 기준), PartyMember.load(남, 기준, 기준), PartyMember.load(나, 기준.plusSeconds(1), 기준)));
        Long 동시합류방 = savedParty(List.of(PartyMember.load(남, 기준, 기준), PartyMember.load(나, 기준, 기준)));
        Long 무관한방 = party(동승자);
        clear();

        Map<Long, List<Long>> result = rideRecords.findMemberIds(List.of(동시합류방, 셋방, 999_999L));

        assertThat(result).containsOnlyKeys(셋방, 동시합류방).doesNotContainKey(무관한방);
        assertThat(result.get(셋방)).containsExactly(남, 나, 동승자);
        assertThat(result.get(동시합류방)).containsExactly(나, 남);
    }

    @Test
    void 방_id_가_비어_있으면_멤버도_빈_결과다() {
        assertThat(rideRecords.findMemberIds(List.of())).isEmpty();
    }

    /** 끝난(FINISHED) 방 - 탄 사람만 기록에 나오므로 멤버는 모두 도착한 것으로 둔다 */
    private Long party(Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for(int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], 기준.plusSeconds(i), 기준));
        }
        return savedParty(members);
    }

    private Long savedParty(List<PartyMember> members) {
        Long partyId = parties.save(Party.load(null, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(3), members, members.getFirst().getMemberId(), 기준, PartyStatus.FINISHED, 12000, 25, "_p~iF~ps|U_ulLnnqC", 0L,
                RoomType.REALTIME, 기준)).getId();
        em.flush();
        // 합류 시각(created_at)은 auditing 이 저장 시각으로 채운다 - 픽스처의 합류 시각을 행에 그대로 옮긴다
        members.forEach(member -> em.getEntityManager()
                .createNativeQuery("UPDATE user_match_room SET created_at = ?1 WHERE match_id = ?2 AND user_id = ?3")
                .setParameter(1, member.getJoinedAt()).setParameter(2, partyId).setParameter(3, member.getMemberId())
                .executeUpdate());
        return partyId;
    }

    private Long saveRide(Long partyId, Instant pickupTime) {
        rideRecords.save(RideRecord.load(partyId, "강남역", "판교역", pickupTime));
        return partyId;
    }

    private void clear() {
        em.flush();
        em.clear();
    }
}
