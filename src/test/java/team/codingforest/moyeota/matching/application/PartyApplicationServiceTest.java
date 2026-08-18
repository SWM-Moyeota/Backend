package team.codingforest.moyeota.matching.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.application.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.application.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.application.dto.PartyResult;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PartyApplicationServiceTest {
    private static final Long host = 1L;
    private static final Long participant = 2L;
    private static final Long anotherHost = 3L;
    private static final Long guest = 4L;

    private PartyApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PartyApplicationService(new PartyJpaTest(), event -> {});
    }

    @Test
    void 방을_생성하면_방장이_멤버로_포함된_ACTIVE_방이_된다() {
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
    void 혼자_남은_방장이_나가면_방이_취소된다() {
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

    private OpenPartyCommand createParty(Long hostId, int capacity) {
        return new OpenPartyCommand(hostId, 37.4979, 127.0276, 37.3948, 127.1112,
                "강남역", "판교역", capacity, 100, 100);
    }
}