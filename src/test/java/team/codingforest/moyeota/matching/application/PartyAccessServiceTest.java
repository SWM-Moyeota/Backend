package team.codingforest.moyeota.matching.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.api.PartyChatSummary;
import team.codingforest.moyeota.matching.domain.Capacity;
import team.codingforest.moyeota.matching.domain.Location;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.Radius;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *  isOnboardingMember - "종료(FINISHED/CANCELED)되지 않은 방의 현재 멤버인가".
 *  방이 없거나 멤버가 아니면 예외 없이 false 를 돌려준다는 점에서 hasMemberOnParty(PARTY_NOT_FOUND 예외)와 다르다.
 */
class PartyAccessServiceTest {
    private static final Long 방장 = 1L;
    private static final Long 동승자 = 2L;
    private static final Long 남남 = 3L;
    private static final Long 기사 = 9L;
    private static final Long 없는방 = 999L;
    private static final Location 강남역 = new Location(37.4979, 127.0276);
    private static final Location 판교역 = new Location(37.3948, 127.1112);
    private static final Radius 기본반경 = new Radius(100);
    private static final Instant 지금 = Instant.now();

    private PartyJpaTest parties;
    private PartyAccessService access;

    @BeforeEach
    void setUp() {
        parties = new PartyJpaTest();
        access = new PartyAccessService(parties);
    }

    // ───────────────────────── 종료 전 상태는 전부 true ─────────────────────────

    @Test
    void 모집_중인_방의_멤버는_true() {
        Long partyId = save(openParty(3));

        assertThat(access.isOnboardingMember(partyId, 방장)).isTrue();
    }

    @Test
    void 정원이_찬_방의_멤버는_true() {
        Long partyId = save(completedParty());

        assertThat(access.isOnboardingMember(partyId, 동승자)).isTrue();
    }

    @Test
    void 매칭_중인_방의_멤버는_true() {
        Long partyId = save(matchingParty());

        assertThat(access.isOnboardingMember(partyId, 방장)).isTrue();
    }

    @Test
    void 기사가_배정된_방의_멤버는_true() {
        Long partyId = save(assignedParty());

        assertThat(access.isOnboardingMember(partyId, 방장)).isTrue();
    }

    @Test
    void 운행_중인_방의_멤버는_true() {
        Long partyId = save(ridingParty());

        assertThat(access.isOnboardingMember(partyId, 동승자)).isTrue();
    }

    // ───────────────────────── 종료되면 false ─────────────────────────

    @Test
    void 운행이_끝난_방의_멤버는_false() {
        Party party = ridingParty();
        party.completeRide(기사, 12000);
        Long partyId = save(party);

        // 멤버 명단은 그대로 남아 있지만(hasMember=true) 상태가 FINISHED 라 false - 상태 조건이 결과를 바꾸는 유일한 경우
        assertThat(parties.findById(partyId).orElseThrow().hasMember(동승자)).isTrue();
        assertThat(access.isOnboardingMember(partyId, 동승자)).isFalse();
    }

    @Test
    void 마지막_멤버가_나가_취소된_방은_false() {
        Party party = openParty(2);
        party.leave(방장);   // 혼자 남은 사람이 나가면 CANCELED
        Long partyId = save(party);

        // CANCELED 는 멤버가 비어야 도달하는 상태라 hasMember 에서 이미 false - 상태 체크까지 가지 않는다
        assertThat(parties.findById(partyId).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(access.isOnboardingMember(partyId, 방장)).isFalse();
    }

    // ───────────────────────── 멤버가 아니면 false ─────────────────────────

    @Test
    void 방에_없는_사람은_false() {
        Long partyId = save(openParty(3));

        assertThat(access.isOnboardingMember(partyId, 남남)).isFalse();
    }

    @Test
    void 나간_사람은_false() {
        Party party = openParty(3);
        party.join(동승자);
        party.leave(동승자);
        Long partyId = save(party);

        assertThat(access.isOnboardingMember(partyId, 동승자)).isFalse();
        assertThat(access.isOnboardingMember(partyId, 방장)).as("남아 있는 사람은 영향 없음").isTrue();
    }

    // ───────────────────────── 방이 없으면 예외 없이 false ─────────────────────────

    @Test
    void 존재하지_않는_방은_예외_없이_false() {
        assertThat(access.isOnboardingMember(없는방, 방장)).isFalse();
    }

    @Test
    void 같은_상황에서_hasMemberOnParty_는_예외를_던진다() {
        // 두 메서드의 계약 차이를 못박아 둔다 - 소비처가 어느 쪽을 써야 하는지 판단 근거
        assertThatThrownBy(() -> access.hasMemberOnParty(없는방, 방장))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_FOUND);
    }

    // ───────────────────────── findChatSummary - 정원이 차기 전에도 조회된다 ─────────────────────────

    /**
     *  1차 배포는 참여할 때마다 채팅방에 넣는다(PartyMemberJoinedEvent). 채팅 쪽이 방 요약으로 채팅방을 만들기 때문에
     *  "정원이 찬 방만" 조회되면 방장·첫 참여자는 채팅방에 못 들어가고 마지막 사람만 들어간다.
     */
    @Test
    void 방장만_있는_방도_채팅_요약이_조회된다() {
        Long partyId = save(openParty(3));

        PartyChatSummary summary = access.findChatSummary(partyId);

        assertThat(summary.id()).isEqualTo(partyId);
        assertThat(summary.users()).containsExactly(방장);
        assertThat(summary.departure()).isEqualTo("강남역");
        assertThat(summary.destination()).isEqualTo("판교역");
    }

    @Test
    void 정원이_덜_찬_방은_지금까지_들어온_멤버만_담긴다() {
        Party party = openParty(3);
        party.join(동승자);
        Long partyId = save(party);

        assertThat(access.findChatSummary(partyId).users()).containsExactly(방장, 동승자);
    }

    @Test
    void 정원이_찬_방의_채팅_요약은_전원이_담긴다() {
        Long partyId = save(completedParty());

        assertThat(access.findChatSummary(partyId).users()).containsExactly(방장, 동승자);
    }

    @Test
    void 없는_방의_채팅_요약은_PARTY_NOT_FOUND() {
        assertThatThrownBy(() -> access.findChatSummary(없는방))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_FOUND);
    }

    // ───────────────────────── 헬퍼 ─────────────────────────

    /** PartyJpaTest.save 는 id 가 박힌 새 인스턴스를 돌려주므로 원본의 getId() 는 null 이다 */
    private Long save(Party party) {
        return parties.save(party).getId();
    }

    private Party openParty(int capacity) {
        return Party.open(방장, 강남역, 판교역, "강남역", "판교역", new Capacity(capacity), 지금, 기본반경, 기본반경, 12000, 25, "_p~iF~ps|U_ulLnnqC");
    }

    /** 정원 2명이 찬 방 */
    private Party completedParty() {
        Party party = openParty(2);
        party.join(동승자);
        return party;
    }

    private Party matchingParty() {
        Party party = completedParty();
        party.startMatching(지금);
        return party;
    }

    private Party assignedParty() {
        Party party = matchingParty();
        party.assignDriver(기사);
        return party;
    }

    private Party ridingParty() {
        Party party = assignedParty();
        party.startRide(기사);
        return party;
    }
}
