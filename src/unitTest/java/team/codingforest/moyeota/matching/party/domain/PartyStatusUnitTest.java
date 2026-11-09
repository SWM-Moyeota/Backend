package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  열린 방 조회(PartyJpa.openStatuses)는 values() 를 isOpen 으로 걸러 IN 조건을 만든다.
 *  상태의 뜻(ACTIVE 모집 중, COMPLETED 약속 확정, FINISHED·CANCELED 닫힘)이 바뀌지 않는지 묶어 둔다
 */
class PartyStatusUnitTest {

    @DisplayName("values - 상태는 ACTIVE·COMPLETED·FINISHED·CANCELED 넷이다")
    @Test
    void values() {

        /*
         * test
         */
        PartyStatus[] statuses = PartyStatus.values();

        /*
         * verify
         */
        assertThat(statuses).containsExactly(PartyStatus.ACTIVE, PartyStatus.COMPLETED, PartyStatus.FINISHED, PartyStatus.CANCELED);
    }

    @DisplayName("isOpen - ACTIVE·COMPLETED 는 열린 방이다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"})
    void isOpen_open(PartyStatus status) {

        /*
         * verify
         */
        assertThat(status.isOpen()).isTrue();
        assertThat(status.isClosed()).isFalse();
    }

    @DisplayName("isOpen - FINISHED·CANCELED 는 닫힌 방이다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void isOpen_closed(PartyStatus status) {

        /*
         * verify
         */
        assertThat(status.isOpen()).isFalse();
        assertThat(status.isClosed()).isTrue();
    }
}
