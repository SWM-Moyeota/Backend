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

    private PartyApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PartyApplicationService(new PartyJpaTest());
    }

    @Test
    void 방을_생성하면_방장이_멤버로_포함된_ACTIVE_방이_된다() {
        PartyResult result = service.open(방생성(3));

        assertThat(result.id()).isNotNull();
        assertThat(result.currentMembers()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void 방에_참여하면_명단에_추가된다() {
        PartyResult result = service.open(방생성(3));

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
        PartyResult party = service.open(방생성(3));
        service.join(party.id(), participant);

        service.leave(party.id(), participant);

        PartyDetailResult detail = service.getPartyDetail(party.id());
        assertThat(detail.currentMembers()).isEqualTo(1);
    }

    @Test
    void 혼자_남은_방장이_나가면_방이_취소된다() {
        PartyResult party = service.open(방생성(3));

        service.leave(party.id(), host);
        assertThat(service.getPartyDetail(party.id()).status()).isEqualTo("CANCELED");
    }

    @Test
    void 활성화된_방만_목록에_나온다() {
        PartyResult openParty = service.open(방생성(3));
        PartyResult closeParty = service.open(방생성(2));

        List<PartyResult> result = service.findActiveParties();

        assertThat(result)
                .extracting(PartyResult::id)
                .containsExactly(openParty.id());
    }

    private OpenPartyCommand 방생성(int capacity) {
        return new OpenPartyCommand(host, 37.4979, 127.0276, 37.3948, 127.1112,
                "강남역", "판교역", capacity, 100, 100);
    }
}