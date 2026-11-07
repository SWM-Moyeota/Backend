package team.codingforest.moyeota.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import team.codingforest.moyeota.faresplit.FareSplitSweeper;
import team.codingforest.moyeota.matching.party.CompletedPartySweeper;
import team.codingforest.moyeota.matching.party.MeetingNotificationSweeper;
import team.codingforest.moyeota.matching.party.PartyRecruitingSweeper;
import team.codingforest.moyeota.matching.party.completion.ChatOnlyCompletionPolicy;
import team.codingforest.moyeota.matching.party.completion.PartyCompletionPolicy;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  승객 모드 고정 - 프로퍼티를 따로 주지 않아도 승객 빈이 뜬다.
 *  정원 충족 정책이 둘 뜨면 PartyService 주입이 모호해 기동이 실패하고, 승객 스윕이 빠지면 끝난 방이 닫히지 않아 멤버가 새 방에 못 들어간다
 */
@SpringBootTest
class PassengerContextTest {
    @Autowired ApplicationContext ctx;

    @DisplayName("PartyCompletionPolicy - 프로퍼티 없이 띄우면 채팅만 정책 하나만 뜬다")
    @Test
    void partyCompletionPolicy_chatOnly() {

        /*
         * test
         */
        Map<String, PartyCompletionPolicy> policies = ctx.getBeansOfType(PartyCompletionPolicy.class);

        /*
         * verify
         */
        assertThat(policies.values()).singleElement().isInstanceOf(ChatOnlyCompletionPolicy.class);
    }

    @DisplayName("승객 스윕 - 프로퍼티 없이 띄우면 자동 종료·모집 만료·약속 알림·택시비 정산 스윕이 모두 뜬다")
    @Test
    void passengerSweepers() {

        /*
         * test
         */
        String[] completed = ctx.getBeanNamesForType(CompletedPartySweeper.class);
        String[] recruiting = ctx.getBeanNamesForType(PartyRecruitingSweeper.class);
        String[] meeting = ctx.getBeanNamesForType(MeetingNotificationSweeper.class);
        String[] fareSplit = ctx.getBeanNamesForType(FareSplitSweeper.class);

        /*
         * verify
         */
        assertThat(completed).as("없으면 아무도 닫지 않은 방의 멤버가 영영 새 방에 못 들어간다").hasSize(1);
        assertThat(recruiting).as("없으면 모집 만료와 예약 출발 전 알림이 멈춘다").hasSize(1);
        assertThat(meeting).as("없으면 약속 시각 알림과 출발 재촉이 멈춘다").hasSize(1);
        assertThat(fareSplit).as("없으면 택시비 송금 재요청·입금 확인 요청·만료가 멈춘다").hasSize(1);
    }
}
