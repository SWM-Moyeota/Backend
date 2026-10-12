package team.codingforest.moyeota.common.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EventResubmitterTest {
    private static final Instant T0 = Instant.parse("2026-09-29T10:00:00Z");
    private static final String MEMBER_LISTENER = "team.codingforest.moyeota.chat.room.application.ChatRoomMatchingListener.on(team.codingforest.moyeota.matching.api.dto.PartyMemberJoinedEvent)";
    private static final String PUSH_LISTENER = "team.codingforest.moyeota.chat.notification.application.ChatNotificationListener.onMessageSent(team.codingforest.moyeota.chat.message.event.ChatMessageSentEvent)";
    private static final String BROADCAST_LISTENER = "team.codingforest.moyeota.chat.message.presentation.ChatMessageBroadcaster.onMessageSent(team.codingforest.moyeota.chat.message.event.ChatMessageSentEvent)";
    private static final String SSE_LISTENER = "team.codingforest.moyeota.matching.sse.PartySseListener.on(team.codingforest.moyeota.matching.api.dto.PartyMemberJoinedEvent)";

    private TargetEventPublication publication(String listenerId, int attempts) {
        TargetEventPublication p = mock(TargetEventPublication.class);
        given(p.getTargetIdentifier()).willReturn(PublicationTargetIdentifier.of(listenerId));
        given(p.getCompletionAttempts()).willReturn(attempts);
        given(p.getPublicationDate()).willReturn(T0);
        return p;
    }

    @Test
    void 백오프_시간이_안_지났으면_건너뛴다() {
        assertThat(EventResubmitter.shouldResubmit(publication(MEMBER_LISTENER, 1), T0.plusSeconds(30))).isFalse();
    }

    @Test
    void 유실되면_안되는_리스너는_백오프가_지나면_재발행한다() {
        Instant later = T0.plusSeconds(3600);

        assertThat(EventResubmitter.shouldResubmit(publication(MEMBER_LISTENER, 1), later)).isTrue();
    }
    @Test
    void 재발행하지_않는_리스너는_백오프가_지나도_건너뛴다() {
        Instant later = T0.plusSeconds(3600);

        assertThat(EventResubmitter.shouldResubmit(publication(BROADCAST_LISTENER, 1), later)).isFalse();
        assertThat(EventResubmitter.shouldResubmit(publication(SSE_LISTENER, 1), later)).isFalse();
        assertThat(EventResubmitter.shouldResubmit(publication(PUSH_LISTENER, 1), later)).isFalse();
    }

    @Test
    void 재발행_제외건_폐기_dead_letter_이동_재발행_순으로_실행되고_집계된다() {
        IncompleteEventPublications publications = mock(IncompleteEventPublications.class);
        EventDeadLetters deadLetters = mock(EventDeadLetters.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        given(deadLetters.discardFailed(anyList())).willReturn(3);
        given(deadLetters.moveExhausted(anyInt())).willReturn(2);

        new EventResubmitter(publications, deadLetters, idleExecutor(), registry).run();

        InOrder order = inOrder(deadLetters, publications);
        order.verify(deadLetters).discardFailed(EventResubmitter.NO_RESUBMIT_LISTENERS);
        order.verify(deadLetters).moveExhausted(EventRetryPolicy.MAX_ATTEMPTS);
        order.verify(publications).resubmitIncompletePublications(any(ResubmissionOptions.class));
        assertThat(registry.counter("event.discarded").count()).isEqualTo(3.0);
        assertThat(registry.counter("event.dead_letter").count()).isEqualTo(2.0);
        assertThat(registry.timer("event.resubmit.duration").count()).isEqualTo(1);
    }

    @Test
    void 실행기_큐가_밀려_있으면_정리만_하고_재발행은_쉰다() {
        IncompleteEventPublications publications = mock(IncompleteEventPublications.class);
        EventDeadLetters deadLetters = mock(EventDeadLetters.class);
        ThreadPoolTaskExecutor busy = executorWithQueued(101);

        new EventResubmitter(publications, deadLetters, busy, new SimpleMeterRegistry()).run();

        verify(deadLetters).discardFailed(EventResubmitter.NO_RESUBMIT_LISTENERS);
        verify(deadLetters).moveExhausted(EventRetryPolicy.MAX_ATTEMPTS);
        verify(publications, never()).resubmitIncompletePublications(any(ResubmissionOptions.class));
        verify(deadLetters, never()).markAbandonedFailed(any(), anyInt());
        busy.shutdown();
    }

    @Test
    void 큐가_비어_있으면_버려진_PUBLISHED_를_FAILED_로_올린_뒤_재발행한다() {
        IncompleteEventPublications publications = mock(IncompleteEventPublications.class);
        EventDeadLetters deadLetters = mock(EventDeadLetters.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        given(deadLetters.markAbandonedFailed(any(), anyInt())).willReturn(4);

        new EventResubmitter(publications, deadLetters, idleExecutor(), registry).run();

        InOrder order = inOrder(deadLetters, publications);
        order.verify(deadLetters).markAbandonedFailed(any(), anyInt());
        order.verify(publications).resubmitIncompletePublications(any(ResubmissionOptions.class));
        assertThat(registry.counter("event.abandoned").count()).isEqualTo(4.0);
    }

    private static ThreadPoolTaskExecutor idleExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setQueueCapacity(5000);
        executor.initialize();
        return executor;
    }

    // 스레드 하나를 막아 두고 큐에 일을 쌓음
    private static ThreadPoolTaskExecutor executorWithQueued(int queued) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(5000);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        CountDownLatch block = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                block.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        for (int i = 0; i < queued; i++) {
            executor.execute(() -> { });
        }
        return executor;
    }
}
