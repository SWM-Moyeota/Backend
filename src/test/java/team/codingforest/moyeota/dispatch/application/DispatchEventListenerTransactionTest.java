package team.codingforest.moyeota.dispatch.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.dispatch.application.event.CallAcceptedEvent;
import team.codingforest.moyeota.dispatch.application.event.CallOpenedEvent;
import team.codingforest.moyeota.dispatch.domain.CallCandidates;
import team.codingforest.moyeota.dispatch.domain.CallNotifier;
import team.codingforest.moyeota.dispatch.domain.DriverLocations;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *  리팩터링의 핵심 보장 - 후보 등록·알림·마감은 "커밋된 뒤에만" 일어난다.
 *  H2 트랜잭션 매니저만 있으면 되므로 Redis/Postgres 없이 돈다.
 *  @DataJpaTest 의 테스트 트랜잭션을 끄고(NOT_SUPPORTED) TransactionTemplate 으로 커밋/롤백을 직접 만든다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({DispatchEventListener.class, DispatchEventListenerTransactionTest.Fakes.class})
class DispatchEventListenerTransactionTest {

    private static final Long 방번호 = 1L;
    private static final PartySummary 강남출발방 =
            new PartySummary(방번호, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 2, 12000, 25, null);

    @TestConfiguration
    static class Fakes {
        @Bean CallCandidates callCandidates() { return new InMemoryCallCandidates(); }
        @Bean DriverLocations driverLocations() { return new FakeDriverLocations(); }
        @Bean CallNotifier callNotifier() { return new RecordingNotifier(); }
    }

    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CallCandidates candidates;
    @Autowired DriverLocations locations;
    @Autowired CallNotifier callNotifier;

    private TransactionTemplate tx;
    private RecordingNotifier notifier;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        notifier = (RecordingNotifier) callNotifier;
        candidates.clear(방번호);
        notifier.notifiedDrivers.clear();
        notifier.closedDrivers.clear();
        notifier.callCount = 0;
    }

    @Test
    void 탐색_트랜잭션이_커밋되면_후보_등록_뒤_콜_알림이_나간다() {
        tx.executeWithoutResult(s -> {
            publisher.publishEvent(new CallOpenedEvent(방번호, List.of(1L, 2L), 강남출발방));

            // 커밋 전: 아직 아무 일도 없어야 한다
            assertThat(candidates.findAll(방번호)).isEmpty();
            assertThat(notifier.callCount).isZero();
        });

        assertThat(candidates.findAll(방번호)).containsExactlyInAnyOrder(1L, 2L);
        assertThat(notifier.notifiedDrivers).containsExactly(1L, 2L);
    }

    @Test
    void 탐색_트랜잭션이_롤백되면_기사에게_콜이_가지_않는다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            publisher.publishEvent(new CallOpenedEvent(방번호, List.of(1L, 2L), 강남출발방));
            throw new IllegalStateException("커밋 직전 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(candidates.findAll(방번호)).as("롤백된 탐색의 후보가 남으면 유령 콜을 수락할 수 있다").isEmpty();
        assertThat(notifier.callCount).as("롤백됐는데 콜 카드가 뜨면 안 된다").isZero();
    }

    @Test
    void 수락_트랜잭션이_커밋되면_후보를_비우고_탈락_기사에게_마감_통지가_간다() {
        candidates.add(방번호, List.of(1L, 2L, 3L));

        tx.executeWithoutResult(s ->
                publisher.publishEvent(new CallAcceptedEvent(방번호, 2L, List.of(1L, 3L))));

        assertThat(candidates.findAll(방번호)).isEmpty();
        assertThat(((FakeDriverLocations) locations).removed).contains(2L);
        assertThat(notifier.closedDrivers).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void 수락_트랜잭션이_롤백되면_콜은_열린_채로_남고_마감_통지도_나가지_않는다() {
        candidates.add(방번호, List.of(1L, 2L, 3L));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            publisher.publishEvent(new CallAcceptedEvent(방번호, 2L, List.of(1L, 3L)));
            throw new IllegalStateException("배정 커밋 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(candidates.findAll(방번호)).as("배정이 안 됐으니 다른 기사가 계속 수락할 수 있어야 한다")
                .containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(notifier.closedDrivers).as("배정도 안 됐는데 마감 통지가 가면 기사들이 콜 카드를 닫아버린다").isEmpty();
    }
}
