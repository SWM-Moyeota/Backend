package team.codingforest.moyeota.matching.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.driver.api.DriverSummary;
import team.codingforest.moyeota.matching.api.MatchingStartedEvent;
import team.codingforest.moyeota.matching.api.PartyClosedEvent;
import team.codingforest.moyeota.matching.api.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.api.PartyMemberLeftEvent;
import team.codingforest.moyeota.matching.application.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.application.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.application.dto.PartyResult;
import team.codingforest.moyeota.matching.domain.Capacity;
import team.codingforest.moyeota.matching.domain.Location;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.Radius;
import team.codingforest.moyeota.matching.domain.RouteEstimate;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.infrastructure.PartySseRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class PartyApplicationServiceTest {
    private static final Long host = 1L;
    private static final Long participant = 2L;
    private static final Long anotherHost = 3L;
    private static final Long guest = 4L;
    private static final Long 기사 = 9L;

    private PartyJpaTest parties;
    private RecordingEventPublisher events;
    private FakeDriverAccess driverAccess;
    private FakeUserAccess userAccess;
    private PartySseRegistry sse;
    private PartyApplicationService service;

    @BeforeEach
    void setUp() {
        parties = new PartyJpaTest();
        events = new RecordingEventPublisher();
        driverAccess = new FakeDriverAccess();
        userAccess = new FakeUserAccess();
        userAccess.등록(host, "방장");
        userAccess.등록(participant, "동승자");
        sse = new PartySseRegistry();
        service = serviceWith(new DispatchCompletionPolicy(events));   // 기본은 발표 모드 - 정원이 차면 배차 시작
    }

    /** 정원 충족 정책만 갈아끼운 서비스. 정책이 쏘는 MatchingStartedEvent 도 같은 기록기에 쌓인다 */
    private PartyApplicationService serviceWith(PartyCompletionPolicy policy) {
        return new PartyApplicationService(parties, events,
                key -> new RouteEstimate(12000, 25, "_p~iF~ps|U_ulLnnqC"),   // RouteFinder 가짜 (네이버 미호출)
                new RouteCacheTest(), driverAccess, userAccess, policy, sse);
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
        // 내부 PK 대신 user 모듈이 준 publicId·닉네임이 실려야 한다
        assertThat(detail.members())
                .extracting(PartyDetailResult.MemberInfo::publicId, PartyDetailResult.MemberInfo::nickname)
                .containsExactlyInAnyOrder(
                        tuple(userAccess.publicIdOf(host), "방장"),
                        tuple(userAccess.publicIdOf(participant), "동승자"));
    }

    @Test
    void 운행이_끝난_방_수가_탑승_횟수로_나온다() {
        // host 는 한 번 완주, participant 는 처음 - 대기 화면 "탑승 N회" 의 근거
        PartyResult 지난방 = service.open(createParty(host, 2));
        service.join(지난방.id(), guest);                       // 정원 2 → 매칭 시작
        Party finished = parties.findById(지난방.id()).orElseThrow();
        finished.assignDriver(기사);
        finished.startRide(기사);
        finished.completeRide(기사, 12000);
        parties.save(finished);

        PartyResult 새방 = service.open(createParty(host, 3));
        service.join(새방.id(), participant);

        assertThat(service.getPartyDetail(새방.id()).members())
                .extracting(PartyDetailResult.MemberInfo::nickname, PartyDetailResult.MemberInfo::rideCount)
                .containsExactlyInAnyOrder(tuple("방장", 1), tuple("동승자", 0));
    }

    @Test
    void 취소된_방은_탑승_횟수에_들어가지_않는다() {
        PartyResult 취소방 = service.open(createParty(host, 3));
        service.leave(취소방.id(), host);                       // 마지막 멤버가 나가면 CANCELED

        PartyResult 새방 = service.open(createParty(host, 3));

        assertThat(service.getPartyDetail(새방.id()).members())
                .extracting(PartyDetailResult.MemberInfo::rideCount)
                .containsExactly(0);
    }

    @Test
    void 유저_요약이_없는_멤버가_있어도_상세_조회는_깨지지_않는다() {
        // 탈퇴한 유저가 명단에 남아 있는 경우 - 조회 자체가 500 으로 죽으면 대기 화면 전체가 막힌다
        PartyResult result = service.open(createParty(host, 3));
        service.join(result.id(), guest);   // guest 는 userAccess 에 미등록

        PartyDetailResult detail = service.getPartyDetail(result.id());

        assertThat(detail.members()).hasSize(2);
        assertThat(detail.members())
                .filteredOn(m -> m.publicId() == null)
                .singleElement()
                .satisfies(m -> assertThat(m.joinedAt()).isNotNull());
    }

    @Test
    void 존재하지_않는_방에_참여하면_예외가_발생한다() {
        assertThatThrownBy(() -> service.join(999L, participant))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_FOUND);
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

    // ── 방 닫힘 이벤트 (SSE 대기 화면이 홈으로 돌아가는 신호) ──

    @Test
    void 마지막_멤버가_나가_취소되면_닫힘_이벤트가_발행된다() {
        PartyResult party = service.open(createParty(host, 3));

        service.leave(party.id(), host);

        assertThat(events.closedFor(party.id(), "CANCELED")).isEqualTo(1);
    }

    @Test
    void 사람이_남아_있으면_나가도_닫힘_이벤트는_없다() {
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), participant);

        service.leave(party.id(), participant);

        assertThat(events.leftFor(party.id(), participant)).isEqualTo(1);
        assertThat(events.closedFor(party.id(), "CANCELED")).as("방은 아직 ACTIVE 다").isZero();
    }

    // ── SSE 구독 권한 ──

    @Test
    void 멤버는_방_변화를_구독할_수_있다() {
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), participant);

        assertThat(service.subscribe(party.id(), participant)).isNotNull();
        assertThat(sse.connections()).isEqualTo(1);
    }

    @Test
    void 멤버가_아니면_구독할_수_없다() {
        PartyResult party = service.open(createParty(host, 3));

        assertThatThrownBy(() -> service.subscribe(party.id(), guest))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_PARTY_MEMBER);
        assertThat(sse.connections()).as("거절된 요청은 연결을 남기지 않는다").isZero();
    }

    @Test
    void 없는_방은_구독할_수_없다() {
        assertThatThrownBy(() -> service.subscribe(999L, host))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_FOUND);
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
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
    }

    @Test
    void 참여_중인_방이_있으면_다른_방에_참여할_수_없다() {
        service.open(createParty(host, 3));
        PartyResult party = service.open(createParty(anotherHost, 3));

        assertThatThrownBy(() -> service.join(party.id(), host))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
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

    // ───────────────────────── 참여·퇴장 이벤트 (채팅방 초대용) ─────────────────────────

    @Test
    void 방을_만들면_방장의_참여_이벤트가_발행된다() {
        PartyResult party = service.open(createParty(host, 3));

        assertThat(events.joinedFor(party.id(), host)).isEqualTo(1);
    }

    @Test
    void 참여하면_정원_충족_여부와_무관하게_참여_이벤트가_발행된다() {
        PartyResult party = service.open(createParty(host, 2));

        service.join(party.id(), participant);   // 정원 충족 → 매칭 시작까지 같이 일어남

        assertThat(events.joinedFor(party.id(), participant))
                .as("정원을 채운 마지막 사람도 채팅방에 들어가야 한다")
                .isEqualTo(1);
        assertThat(events.matchingStartedFor(party.id())).isEqualTo(1);
    }

    @Test
    void 나가면_퇴장_이벤트가_발행된다() {
        PartyResult party = service.open(createParty(host, 3));
        service.join(party.id(), participant);

        service.leave(party.id(), participant);

        assertThat(events.leftFor(party.id(), participant)).isEqualTo(1);
    }

    @Test
    void 혼자_타는_방은_참여_이벤트_없이_바로_매칭을_시작한다() {
        PartyResult party = service.open(createParty(host, 1));

        assertThat(events.joinedFor(party.id(), host))
                .as("두 번째 사람이 올 수 없어 채팅방이 생길 일이 없다 - 초대 이벤트도 없다")
                .isZero();
        assertThat(events.matchingStartedFor(party.id())).isEqualTo(1);
    }

    // ───────────────────────── 배포 모드 (TAXI_ENABLED=false) ─────────────────────────

    /** 기사 기능이 없는 1차 배포. 정원이 차도 배차 없이 COMPLETED 에 머물러야 한다 - MATCHING 으로 가면 받는 리스너가 없어 계정이 잠긴다 */
    @Nested
    class 택시_기능이_꺼진_배포_모드 {

        @BeforeEach
        void 배포_정책으로_교체() {
            service = serviceWith(new ChatOnlyCompletionPolicy());
        }

        @Test
        void 정원이_차도_매칭을_시작하지_않고_COMPLETED에_머문다() {
            PartyResult party = service.open(createParty(host, 2));

            service.join(party.id(), participant);

            assertThat(service.getPartyDetail(party.id()).status()).isEqualTo("COMPLETED");
            assertThat(events.matchingStartedFor(party.id())).as("배차 신호가 나가면 안 된다").isZero();
        }

        @Test
        void 혼자_타는_방도_COMPLETED에_머문다() {
            PartyResult party = service.open(createParty(host, 1));

            assertThat(service.getPartyDetail(party.id()).status()).isEqualTo("COMPLETED");
            assertThat(events.matchingStartedFor(party.id())).isZero();
        }

        @Test
        void 참여_이벤트는_모드와_무관하게_발행된다() {
            PartyResult party = service.open(createParty(host, 2));

            service.join(party.id(), participant);

            assertThat(events.joinedFor(party.id(), host)).isEqualTo(1);
            assertThat(events.joinedFor(party.id(), participant)).isEqualTo(1);
        }

        // ── 합승 완료 (참여자가 직접) ──

        @Test
        void 참여자가_합승_완료하면_FINISHED가_된다() {
            PartyResult party = service.open(createParty(host, 2));
            service.join(party.id(), participant);

            service.finish(party.id(), participant);

            assertThat(service.getPartyDetail(party.id()).status()).isEqualTo("FINISHED");
        }

        @Test
        void 합승_완료한_뒤에는_같은_사람이_새_방을_만들_수_있다() {
            // 이게 안 되면 배포 모드에서 계정이 잠긴다 - 이 기능의 존재 이유
            PartyResult party = service.open(createParty(host, 2));
            service.join(party.id(), participant);
            service.finish(party.id(), host);

            PartyResult next = service.open(createParty(host, 3));

            assertThat(next.id()).isNotEqualTo(party.id());
        }

        @Test
        void 모집_중인_방은_합승_완료할_수_없다() {
            PartyResult party = service.open(createParty(host, 3));

            assertThatThrownBy(() -> service.finish(party.id(), host))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MatchingErrorCode.PARTY_NOT_COMPLETED);
        }

        @Test
        void 방에_없는_사람은_합승_완료할_수_없다() {
            PartyResult party = service.open(createParty(host, 2));
            service.join(party.id(), participant);

            assertThatThrownBy(() -> service.finish(party.id(), guest))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MatchingErrorCode.NOT_PARTY_MEMBER);
        }

        // ── 자동 종료 스윕 ──

        @Test
        void 정원이_찬_뒤_오래_방치된_방만_자동_종료된다() {
            Long 오래된방 = saveCompleted(Instant.now().minus(Duration.ofDays(1)), null);   // anotherHost·guest 가 여기 묶여 있다
            PartyResult 방금찬방 = service.open(createParty(host, 2));
            service.join(방금찬방.id(), participant);

            new CompletedPartySweeper(parties, service).sweep();

            assertThat(parties.findById(오래된방).orElseThrow().getStatus()).isEqualTo(PartyStatus.FINISHED);
            assertThat(parties.findById(방금찬방.id()).orElseThrow().getStatus()).as("TTL 전이면 건드리지 않는다").isEqualTo(PartyStatus.COMPLETED);
        }

        @Test
        void 한_방이_실패해도_나머지_방은_닫힌다() {
            Long 기사있는방 = saveCompleted(Instant.now().minus(Duration.ofDays(1)), 기사);   // DRIVER_ALREADY_ASSIGNED 로 실패할 방
            Long 정상방 = saveCompleted(Instant.now().minus(Duration.ofDays(1)), null);

            new CompletedPartySweeper(parties, service).sweep();   // 예외가 새어 나오면 여기서 터진다

            assertThat(parties.findById(기사있는방).orElseThrow().getStatus()).isEqualTo(PartyStatus.COMPLETED);
            assertThat(parties.findById(정상방).orElseThrow().getStatus()).isEqualTo(PartyStatus.FINISHED);
        }

        @Test
        void 합승_완료하면_FINISHED_닫힘_이벤트가_발행된다() {
            PartyResult party = service.open(createParty(host, 2));
            service.join(party.id(), participant);

            service.finish(party.id(), participant);

            assertThat(events.closedFor(party.id(), "FINISHED")).isEqualTo(1);
        }

        @Test
        void 자동_종료해도_FINISHED_닫힘_이벤트가_발행된다() {
            Long 오래된방 = saveCompleted(Instant.now().minus(Duration.ofDays(1)), null);

            service.expire(오래된방);

            assertThat(events.closedFor(오래된방, "FINISHED")).isEqualTo(1);
        }

        @Test
        void 이미_닫힌_방을_스윕이_다시_닫으려_하면_거부된다() {
            PartyResult party = service.open(createParty(host, 2));
            service.join(party.id(), participant);
            service.finish(party.id(), host);

            assertThatThrownBy(() -> service.expire(party.id()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(MatchingErrorCode.PARTY_NOT_COMPLETED);
        }

        /** 정원 2가 찬 방을 원하는 충족 시각으로 저장 - 도메인은 Instant.now() 만 찍으므로 과거 시각은 restore 로만 만들 수 있다 */
        private Long saveCompleted(Instant completedAt, Long driverId) {
            Location 강남역 = new Location(37.4979, 127.0276);
            Location 판교역 = new Location(37.3948, 127.1112);
            Party seed = Party.open(anotherHost, 강남역, 판교역, "강남역", "판교역", new Capacity(2),
                    completedAt, new Radius(100), new Radius(100), 12000, 25, "_p~iF~ps|U_ulLnnqC");
            seed.join(guest);   // 멤버 목록만 빌려 쓴다 (PartyMember 생성자는 도메인 패키지 밖에서 못 부른다)

            Party party = Party.restore(null, 강남역, 판교역, new Radius(100), new Radius(100), "강남역", "판교역", new Capacity(2),
                    seed.getMembers(), completedAt, PartyStatus.COMPLETED, 12000, 25, "_p~iF~ps|U_ulLnnqC", driverId, null, completedAt);
            return parties.save(party).getId();
        }
    }

    @Test
    void 매칭중인_유저는_새_방을_만들_수_없다() {
        // 기사를 기다리는 중에도 방에 묶여 있어야 한다 (isOngoing 확장 검증)
        PartyResult party = service.open(createParty(host, 2));
        service.join(party.id(), participant);   // MATCHING 진입

        assertThatThrownBy(() -> service.open(createParty(participant, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
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
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.DRIVER_NOT_ASSIGNED);
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

    // ───────────────────────── 지도 영역(뷰포트) 조회 ─────────────────────────

    @Test
    void 영역_안에서_출발하는_방만_조회된다() {
        service.open(createPartyAt(host, 37.4979, 127.0276, 3));        // 강남역 - 영역 안
        service.open(createPartyAt(anotherHost, 37.5665, 126.9780, 3)); // 시청 - 영역 밖

        List<PartyResult> result = service.findActivePartiesWithin(37.49, 127.02, 37.51, 127.06);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).departure()).isEqualTo("강남역");
    }

    @Test
    void 영역_모서리에_정확히_걸친_방도_조회된다() {
        // between은 양끝 포함 - 경계 좌표가 빠지면 화면 가장자리의 마커가 사라진다
        service.open(createPartyAt(host, 37.49, 127.02, 3));

        assertThat(service.findActivePartiesWithin(37.49, 127.02, 37.51, 127.06)).hasSize(1);
    }

    @Test
    void 영역_안이어도_모집이_끝난_방은_조회되지_않는다() {
        // 정원이 차서 매칭으로 넘어간 방 - 지도에 마커가 남아 있으면 못 타는 방을 탭하게 된다
        PartyResult party = service.open(createPartyAt(host, 37.4979, 127.0276, 2));
        service.join(party.id(), participant);   // 정원 충족 → COMPLETED + 자동 매칭

        assertThat(service.findActivePartiesWithin(37.49, 127.02, 37.51, 127.06)).isEmpty();
    }

    @Test
    void 뒤집힌_영역_좌표로는_조회할_수_없다() {
        // 남서가 북동보다 크면 between 결과가 항상 빈 목록 - 프론트 좌표 순서 실수를 조용히 삼키지 않고 알려준다
        assertThatThrownBy(() -> service.findActivePartiesWithin(37.51, 127.06, 37.49, 127.02))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.INVALID_MAP_BOUNDS);
    }

    private OpenPartyCommand createPartyAt(Long creatorId, double lat, double lng, int capacity) {
        return new OpenPartyCommand(creatorId, lat, lng, 37.3948, 127.1112,
                "강남역", "판교역", capacity, 100, 100);
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

        long joinedFor(Long partyId, Long memberId) {
            return published.stream()
                    .filter(e -> e instanceof PartyMemberJoinedEvent j && j.partyId().equals(partyId) && j.memberId().equals(memberId))
                    .count();
        }

        long closedFor(Long partyId, String status) {
            return published.stream()
                    .filter(e -> e instanceof PartyClosedEvent c && c.partyId().equals(partyId) && c.status().equals(status))
                    .count();
        }

        long leftFor(Long partyId, Long memberId) {
            return published.stream()
                    .filter(e -> e instanceof PartyMemberLeftEvent l && l.partyId().equals(partyId) && l.memberId().equals(memberId))
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

        @Override
        public Optional<Long> findUserId(Long driverId) {
            return Optional.empty();
        }
    }
}
