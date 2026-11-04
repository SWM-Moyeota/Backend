package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartyReservationTest {
    private static final Long 방장 = 1L;
    private static final Long 참여자 = 2L;
    private static final Location 강남역 = new Location(37.4979, 127.0276);
    private static final Location 판교역 = new Location(37.3948, 127.1112);
    private static final Instant 지금 = Instant.now();

    private Party reserve(Instant createdAt, Instant departureAt) {
        return Party.reserve(방장, 강남역, 판교역, "강남역", "판교역", new Capacity(3), createdAt, departureAt);
    }

    private Party reserveTomorrow() {
        return reserve(지금, 지금.plus(Duration.ofDays(1)));
    }

    private Party realtime(int capacity) {
        return Party.open(방장, 강남역, 판교역, "강남역", "판교역", new Capacity(capacity), 지금, Radius.DEFAULT, Radius.DEFAULT, 12000, 25, "encoded-path");
    }

    private void assertFails(Runnable action, MatchingErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }

    // ───────────────────────── 생성 ─────────────────────────

    @Test
    void 예약_방은_출발_시각을_갖고_경로와_요금과_시간은_비어_있다() {
        Instant 출발 = 지금.plus(Duration.ofDays(1));

        Party party = reserve(지금, 출발);

        assertThat(party.getRoomType()).isEqualTo(RoomType.RESERVED);
        assertThat(party.getDepartureAt()).isEqualTo(출발);
        assertThat(party.getRoute()).isNull();
        assertThat(party.getEstimatedFare()).isNull();
        assertThat(party.getEstimatedTime()).isNull();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.hasMember(방장)).isTrue();
    }

    @Test
    void 실시간_방은_출발_시각이_없다() {
        Party party = realtime(3);

        assertThat(party.getRoomType()).isEqualTo(RoomType.REALTIME);
        assertThat(party.getDepartureAt()).isNull();
    }

    @Test
    void 이미_지난_시각으로는_예약할_수_없다() {
        assertFails(() -> reserve(지금, 지금.minusSeconds(1)), MatchingErrorCode.INVALID_DEPARTURE_TIME);
    }

    @Test
    void 지금_이_순간으로는_예약할_수_없다() {
        assertFails(() -> reserve(지금, 지금), MatchingErrorCode.INVALID_DEPARTURE_TIME);
    }

    @Test
    void 칠일이_넘는_시각으로는_예약할_수_없다() {
        assertFails(() -> reserve(지금, 지금.plus(Duration.ofDays(7)).plusSeconds(1)), MatchingErrorCode.INVALID_DEPARTURE_TIME);
    }

    @Test
    void 정확히_칠일_뒤로는_예약할_수_있다() {
        Instant 출발 = 지금.plus(Duration.ofDays(7));

        assertThat(reserve(지금, 출발).getDepartureAt()).isEqualTo(출발);
    }

    @Test
    void 예약_방도_출발지와_도착지가_같으면_만들_수_없다() {
        assertFails(() -> Party.reserve(방장, 강남역, 강남역, "강남역", "강남역", new Capacity(3), 지금, 지금.plus(Duration.ofDays(1))),
                MatchingErrorCode.SAME_DEPARTURE_DESTINATION);
    }

    // ───────────────────────── 참여 ─────────────────────────

    @Test
    void 출발_시각이_지난_예약_방에는_참여할_수_없다() {
        Party party = reserve(지금.minus(Duration.ofHours(2)), 지금.minus(Duration.ofHours(1)));

        assertFails(() -> party.join(참여자), MatchingErrorCode.PARTY_CLOSED);
    }

    @Test
    void 출발_전인_예약_방에는_참여할_수_있다() {
        Party party = reserveTomorrow();

        party.join(참여자);

        assertThat(party.hasMember(참여자)).isTrue();
    }

    // ───────────────────────── 모집 시간 만료 ─────────────────────────

    @Test
    void 모집_시간이_끝났을_때_두_명_이상이면_확정된다() {
        Party party = realtime(3);
        party.join(참여자);

        party.expireRecruiting();

        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getCompletedAt()).isNotNull();
    }

    @Test
    void 모집_시간이_끝났을_때_혼자면_방이_취소된다() {
        Party party = realtime(3);

        party.expireRecruiting();

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @Test
    void 이미_정원이_찬_방은_모집_만료_대상이_아니다() {
        Party party = realtime(2);
        party.join(참여자);

        assertFails(party::expireRecruiting, MatchingErrorCode.PARTY_CLOSED);
    }

    // ───────────────────────── 합승 종료 ─────────────────────────

    // 예약 방은 만들 때부터 약속이 정해진 방이라 출발 시각과 상관없이 이전 합승 종료 경로로는 끝낼 수 없다 - 출발(depart)로만 끝낸다

    @Test
    void 출발이_한참_남은_예약_방은_합승_종료_대신_출발로_끝내라고_거절한다() {
        Party party = Party.reserve(방장, 강남역, 판교역, "강남역", "판교역", new Capacity(2), 지금, 지금.plus(Duration.ofHours(1)));
        party.join(참여자);

        assertFails(() -> party.finishWithoutDriver(방장), MatchingErrorCode.USE_DEPART_FOR_CONFIRMED_PARTY);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    @Test
    void 출발이_다가온_예약_방도_합승_종료_대신_출발로_끝내라고_거절한다() {
        Party party = Party.reserve(방장, 강남역, 판교역, "강남역", "판교역", new Capacity(2), 지금, 지금.plus(Duration.ofMinutes(10)));
        party.join(참여자);

        assertFails(() -> party.finishWithoutDriver(방장), MatchingErrorCode.USE_DEPART_FOR_CONFIRMED_PARTY);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    // ───────────────────────── 출발 전 알림 ─────────────────────────

    @Test
    void 알림을_보내면_보낸_시각이_기록된다() {
        Party party = reserveTomorrow();

        party.markReminderSent(지금);

        assertThat(party.getReminderSentAt()).isEqualTo(지금);
    }
}
