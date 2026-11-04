package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  진행 중 방 조회(PartyJpa.ongoingStatuses)는 values() 를 isOngoing 으로 걸러 IN 조건을 만든다.
 *  택시 상태를 지운 뒤에도 남은 상태의 뜻(ACTIVE·COMPLETED 는 진행 중, FINISHED·CANCELED 는 닫힘)이 그대로인지 묶어 둔다
 */
class PartyStatusUnitTest {

    @DisplayName("values - 남은 상태는 ACTIVE·COMPLETED·FINISHED·CANCELED 넷이다")
    @Test
    void values() {

        /*
         * settings
         */
        // 없음

        /*
         * test
         */
        PartyStatus[] statuses = PartyStatus.values();

        /*
         * verify
         */
        assertThat(statuses).containsExactly(PartyStatus.ACTIVE, PartyStatus.COMPLETED, PartyStatus.FINISHED, PartyStatus.CANCELED);
    }

    @DisplayName("isOngoing - ACTIVE·COMPLETED 는 진행 중이고 닫히지 않았다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"})
    void isOngoing_open(PartyStatus status) {

        /*
         * settings
         */
        // status 는 파라미터로 받는다

        /*
         * test
         */
        boolean ongoing = status.isOngoing();

        /*
         * verify
         */
        assertThat(ongoing).isTrue();
        assertThat(status.isClosed()).isFalse();
    }

    @DisplayName("isOngoing - FINISHED·CANCELED 는 닫혀 진행 중이 아니다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void isOngoing_closed(PartyStatus status) {

        /*
         * settings
         */
        // status 는 파라미터로 받는다

        /*
         * test
         */
        boolean ongoing = status.isOngoing();

        /*
         * verify
         */
        assertThat(ongoing).isFalse();
        assertThat(status.isClosed()).isTrue();
    }

    @DisplayName("isOngoing - 진행 중 상태 목록은 ACTIVE·COMPLETED 이고 모집 상태 목록과 같다")
    @Test
    void isOngoing_sameAsRecruiting() {

        /*
         * settings
         */
        List<PartyStatus> all = Arrays.asList(PartyStatus.values());

        /*
         * test
         */
        List<PartyStatus> ongoing = all.stream().filter(PartyStatus::isOngoing).toList();
        List<PartyStatus> recruiting = all.stream().filter(PartyStatus::isRecruiting).toList();

        /*
         * verify
         */
        assertThat(ongoing).containsExactly(PartyStatus.ACTIVE, PartyStatus.COMPLETED);
        assertThat(recruiting).isEqualTo(ongoing);
    }
}
