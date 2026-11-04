package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.api.dto.PartyChatSummary;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.Radius;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class PartyAccessServiceUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long STRANGER_ID = 3L;
    private static final Instant BASE_TIME = Instant.parse("2026-10-01T00:00:00Z");

    @Mock
    private Parties parties;

    @InjectMocks
    private PartyAccessService partyAccessService;

    // ───────────────────────── findChatSummary ─────────────────────────

    @DisplayName("findChatSummary - 합류 순 멤버와 장소를 돌려준다")
    @Test
    void findChatSummary() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.COMPLETED, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        PartyChatSummary result = partyAccessService.findChatSummary(PARTY_ID);

        /*
         * verify
         */
        assertThat(result).isEqualTo(new PartyChatSummary(PARTY_ID, List.of(HOST_ID, MEMBER_ID), "강남역", "판교역"));
    }

    @DisplayName("findChatSummary - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void findChatSummary_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.findChatSummary(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
    }

    // ───────────────────────── isOnboardingMember ─────────────────────────

    @DisplayName("isOnboardingMember - 멤버이고 진행 중인 상태면 true")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"}, mode = EnumSource.Mode.EXCLUDE)
    void isOnboardingMember(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isTrue();
    }

    @DisplayName("isOnboardingMember - 멤버여도 닫힌 방이면 false")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void isOnboardingMember_closed(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("isOnboardingMember - 진행 중이어도 멤버가 아니면 false")
    @Test
    void isOnboardingMember_notMember() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.ACTIVE, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, STRANGER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("isOnboardingMember - 없는 방이면 예외 없이 false")
    @Test
    void isOnboardingMember_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    // ───────────────────────── 공통 도우미 ─────────────────────────

    /** memberIds 앞쪽일수록 먼저 합류한 멤버다. 정원은 3 으로 고정 */
    private static Party party(PartyStatus status, Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for (int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], BASE_TIME.plusSeconds(i)));
        }

        return Party.load(PARTY_ID, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역",
                new Capacity(3), members, BASE_TIME, status, 12000, 25, "encoded-path", null, 0L);
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
