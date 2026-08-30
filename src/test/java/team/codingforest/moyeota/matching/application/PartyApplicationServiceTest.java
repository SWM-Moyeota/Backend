package team.codingforest.moyeota.matching.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.driver.api.DriverSummary;
import team.codingforest.moyeota.matching.api.MatchingStartedEvent;
import team.codingforest.moyeota.matching.application.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.application.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.application.dto.PartyResult;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.RouteEstimate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class PartyApplicationServiceTest {
    private static final Long host = 1L;
    private static final Long participant = 2L;
    private static final Long anotherHost = 3L;
    private static final Long guest = 4L;
    private static final Long 기사 = 9L;

    private PartyJpaTest parties;
    private RecordingEventPublisher events;
    private FakeDriverAccess driverAccess;
    private PartyApplicationService service;

    @BeforeEach
    void setUp() {
        parties = new PartyJpaTest();
        events = new RecordingEventPublisher();
        driverAccess = new FakeDriverAccess();
        service = new PartyApplicationService(parties, events,
                key -> new RouteEstimate(12000, 25, "_p~iF~ps|U_ulLnnqC"),   // RouteFinder 가짜 (네이버 미호출)
                new RouteCacheTest(), driverAccess);
    }

    @Test
    void 방을_생성하면_생성자가_멤버로_포함된_ACTIVE_방이_된다() {
        PartyResult result = service.open(createParty(host, 3));

        assertThat(result.id()).isNotNull();
        assertThat(result.currentMembers()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void 방에_참여하면_명단에_추가된다() {
        PartyResult result = service.open(createParty(host, 3));

        service.join(result.id(), participant);
        PartyDetailResult detail = service.getPartyDetail(result.id());
        assertThat(detail.currentMembers()).isEqualTo(2);
        assertThat(detail.members())
                .extracting(PartyDetailResult.MemberInfo::memberId)
                .containsExactlyInAnyOrder(host, participant);
    }

    @Test
    void 존재하지_않는_방에_참여하면_예외가_발생한다() {
        assertThatThrownBy(() -> service.join(999L, participant))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 나가면_명단에서_빠진다() {
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), participant);

        service.leave(party.id(), participant);

        PartyDetailResult detail = service.getPartyDetail(party.id());
        assertThat(detail.currentMembers()).isEqualTo(1);
    }

    @Test
    void 혼자_남은_사람이_나가면_방이_취소된다() {
        PartyResult party = service.open(createParty(host, 3));

        service.leave(party.id(), host);
        assertThat(service.getPartyDetail(party.id()).status()).isEqualTo("CANCELED");
    }

    @Test
    void 활성화된_방만_목록에_나온다() {
        PartyResult openParty = service.open(createParty(host, 3));
        PartyResult closeParty = service.open(createParty(anotherHost, 2));

        service.join(closeParty.id(), guest);

        List<PartyResult> result = service.findActiveParties();

        assertThat(result)
                .extracting(PartyResult::id)
                .containsExactly(openParty.id());
    }

    @Test
    void 참여_중인_방이_있으면_새_방을_만들_수_없다() {
        service.open(createParty(host, 3));

        assertThatThrownBy(() -> service.open(createParty(host, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 참여_중인_방이_있으면_다른_방에_참여할_수_없다() {
        service.open(createParty(host, 3));
        PartyResult party = service.open(createParty(anotherHost, 3));

        assertThatThrownBy(() -> service.join(party.id(), host))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 방이_취소된_뒤에는_새_방을_만들_수_있다() {
        PartyResult party = service.open(createParty(host, 3));

        service.leave(party.id(), host);

        PartyResult newParty = service.open(createParty(host, 3));

        assertThat(newParty.id()).isNotEqualTo(party.id());
    }

    // ───────────────────────── 자동 매칭 시작 ─────────────────────────

    @Test
    void 정원이_차면_자동으로_매칭이_시작된다() {
        PartyResult party = service.open(createParty(host, 2));

        service.join(party.id(), participant);

        assertThat(service.getPartyDetail(party.id()).status()).isEqualTo("MATCHING");
        assertThat(events.matchingStartedFor(party.id())).isEqualTo(1);   // 배차가 정확히 한 번 트리거된다
    }

    @Test
    void 혼자_타는_방을_만들면_즉시_매칭이_시작된다() {
        PartyResult party = service.open(createParty(host, 1));

        assertThat(service.getPartyDetail(party.id()).status()).isEqualTo("MATCHING");
        assertThat(events.matchingStartedFor(party.id())).isEqualTo(1);
    }

    @Test
    void 정원이_차지_않으면_매칭_이벤트가_발행되지_않는다() {
        PartyResult party = service.open(createParty(host, 3));

        service.join(party.id(), participant);

        assertThat(events.matchingStartedFor(party.id())).isZero();
    }

    @Test
    void 매칭중인_유저는_새_방을_만들_수_없다() {
        // 기사를 기다리는 중에도 방에 묶여 있어야 한다 (isOngoing 확장 검증)
        PartyResult party = service.open(createParty(host, 2));
        service.join(party.id(), participant);   // MATCHING 진입

        assertThatThrownBy(() -> service.open(createParty(participant, 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 해산된_방의_멤버는_즉시_새_방을_만들_수_있다() {
        // 3분 매칭 실패로 해산되면 유저가 방에 묶여 있으면 안 된다
        PartyResult party = service.open(createParty(host, 2));
        service.join(party.id(), participant);   // 정원 충족 → MATCHING

        Party failed = parties.findById(party.id()).orElseThrow();
        failed.failMatching();                   // 스위퍼의 3분 타임아웃 해산 재현
        parties.save(failed);

        PartyResult newParty = service.open(createParty(host, 2));

        assertThat(newParty.id()).isNotEqualTo(party.id());
    }

    @Test
    void 운행이_끝난_유저는_새_방을_만들_수_있다() {
        PartyResult party = service.open(createParty(host, 2));
        service.join(party.id(), participant);

        Party riding = parties.findById(party.id()).orElseThrow();
        riding.assignDriver(기사);
        riding.startRide(기사);
        riding.completeRide(기사, 15000);
        parties.save(riding);

        PartyResult newParty = service.open(createParty(host, 2));

        assertThat(newParty.id()).isNotEqualTo(party.id());
    }

    // ───────────────────────── 배정 기사 조회 ─────────────────────────

    @Test
    void 기사_배정_전에는_기사_정보를_조회할_수_없다() {
        PartyResult party = service.open(createParty(host, 2));
        service.join(party.id(), participant);

        assertThatThrownBy(() -> service.getAssignDriver(party.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 기사가_배정되면_차량_정보를_조회할_수_있다() {
        PartyResult party = service.open(createParty(host, 2));
        service.join(party.id(), participant);

        Party matched = parties.findById(party.id()).orElseThrow();
        matched.assignDriver(기사);
        parties.save(matched);
        driverAccess.summary = new DriverSummary(4, "12가3456", "중형");

        DriverSummary summary = service.getAssignDriver(party.id());

        assertThat(summary.plateNumber()).isEqualTo("12가3456");
    }

    private OpenPartyCommand createParty(Long creatorId, int capacity) {
        return new OpenPartyCommand(creatorId, 37.4979, 127.0276, 37.3948, 127.1112,
                "강남역", "판교역", capacity, 100, 100);
    }

    /** 발행된 이벤트를 기록하는 가짜 발행기 */
    static class RecordingEventPublisher implements ApplicationEventPublisher {
        private final List<Object> published = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            published.add(event);
        }

        long matchingStartedFor(Long partyId) {
            return published.stream()
                    .filter(e -> e instanceof MatchingStartedEvent m && m.partyId().equals(partyId))
                    .count();
        }
    }

    /** 기사 정보 조회용 가짜 - summary를 넣어두면 그걸 반환 */
    static class FakeDriverAccess implements DriverAccess {
        DriverSummary summary;

        @Override
        public boolean canReceiveCalls(Long driverId) {
            return true;
        }

        @Override
        public Map<Long, String> findFcmTokens(List<Long> driverIds) {
            return Map.of();
        }

        @Override
        public Optional<DriverSummary> findSummary(Long driverId) {
            return Optional.ofNullable(summary);
        }
    }
}
