package team.codingforest.moyeota.matching.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class PartyTest {
    private static final Long 방장 = 1L;
    private static final Location 강남역 = new Location(37.4979, 127.0276);
    private static final Location 판교역 = new Location(37.3948, 127.1112);
    private static final String 강남역출발 = new String("강남역");
    private static final String 판교역도착 = new String("판교역");
    private static final Radius 기본반경 = new Radius(100);
    private static final Instant 지금 = Instant.now();

    private Party openParty(int capacity) {
        return Party.open(방장, 강남역, 판교역, 강남역출발, 판교역도착, new Capacity(capacity), 지금, 기본반경, 기본반경);
    }

    @Test
    void 정원이_차면_자동으로_마감된다() {
        Party party = openParty(2);

        party.join(2L);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    @Test
    void 마감된_방에는_참여할_수_없다() {
        Party party = openParty(2);
        party.join(2L);

        assertThatThrownBy(() -> party.join(3L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_회원은_중복_참여할_수_없다() {
        Party party = openParty(2);

        assertThatThrownBy(() -> party.join(1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 참여자가_나가면_명단에서_빠진다() {
        Party party = openParty(2);

        party.join(2L);
        party.leave(2L);

        assertThat(party.hasMember(2L)).isFalse();
        assertThat(party.getMembers()).hasSize(1);
    }

    @Test
    void 방장이_나가면_가장_먼저_들어온_사람이_방장이_된다() {
        Party party = openParty(3);

        party.join(2L);
        party.join(3L);

        party.leave(1L);

        assertThat(party.getHostId()).isEqualTo(2L);
        assertThat(party.hasMember(1L)).isFalse();
    }

    @Test
    void 마지막_남은_사람이_나가면_방이_취소된다() {
        Party party = openParty(1);

        party.leave(1L);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @Test
    void 마감된_방에서_누군가_나가면_다시_모집중이_된다() {
        Party party = openParty(2);

        party.join(2L);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);

        party.leave(2L);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
    }

    @Test
    void 참여하지_않은_회원은_나갈_수_없다() {
        Party party = openParty(2);

        assertThatThrownBy(() -> party.leave(2L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 방장은_방을_취소할_수_있다() {
        Party party = openParty(3);
        party.join(2L);

        party.cancel(1L);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @Test
    void 방장이_아니면_취소할_수_없다() {
        Party party = openParty(2);

        party.join(2L);

        assertThatThrownBy(() -> party.cancel(2L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 마감된_방도_방장은_취소할_수_있다() {
        Party party = openParty(2);
        party.join(2L);

        party.cancel(1L);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @Test
    void 준비_완료를_누르면_READY_상태가_된다() {
        Party party = openParty(2);
        party.join(2L);

        party.ready(2L);

        assertThat(findMember(party, 2L).isReady()).isTrue();
    }

    @Test
    void 준비_취소를_누르면_NOT_READY로_돌아간다() {
        Party party = openParty(2);
        party.join(2L);
        party.ready(2L);

        party.cancelReady(2L);

        assertThat(findMember(party, 2L).isReady()).isFalse();
    }

    @Test
    void 방에_없는_회원은_준비할_수_없다() {
        Party party = openParty(2);

        assertThatThrownBy(() -> party.ready(2L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 정원이_차고_전원_준비되면_매칭을_시작할_수_있다() {
        Party party = openParty(2);
        party.join(2L);
        party.ready(1L);
        party.ready(2L);

        party.startMatching(1L);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.MATCHING);
    }

    @Test
    void 방장이_아니면_매칭을_시작할_수_없다() {
        Party party = openParty(2);
        party.join(2L);
        party.ready(1L);
        party.ready(2L);

        assertThatThrownBy(() -> party.startMatching(2L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 정원이_차지_않으면_매칭을_시작할_수_없다() {
        Party party = openParty(3);
        party.join(2L);
        party.ready(1L);
        party.ready(2L);

        assertThatThrownBy(() -> party.startMatching(1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 전원_준비가_아니면_매칭을_시작할_수_없다() {
        Party party = openParty(2);
        party.join(2L);
        party.ready(1L);

        assertThatThrownBy(() -> party.startMatching(1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 준비를_취소하면_매칭을_시작할_수_없다() {
        Party party = openParty(2);
        party.join(2L);
        party.ready(1L);
        party.ready(2L);
        party.cancelReady(2L);

        assertThatThrownBy(() -> party.startMatching(1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 매칭이_시작된_방에서는_준비_상태를_바꿀_수_없다() {
        Party party = openParty(2);
        party.join(2L);
        party.ready(1L);
        party.ready(2L);
        party.startMatching(1L);

        assertThatThrownBy(() -> party.cancelReady(2L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> party.ready(2L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 새로_참여한_회원은_준비_전_상태로_시작한다() {
        Party party = openParty(2);

        party.join(2L);

        assertThat(findMember(party, 2L).isReady()).isFalse();
    }

    private PartyMember findMember(Party party, Long memberId) {
        return party.getMembers().stream()
                .filter(m -> m.getMemberId().equals(memberId))
                .findFirst()
                .orElseThrow();
    }
}