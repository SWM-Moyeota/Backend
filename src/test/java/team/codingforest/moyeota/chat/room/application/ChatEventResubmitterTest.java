package team.codingforest.moyeota.chat.room.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;
import team.codingforest.moyeota.chat.room.domain.ChatEventDeadLetters;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ChatEventResubmitterTest {
    private static final Instant T0 = Instant.parse("2026-09-29T10:00:00Z");
    private static final String MEMBER_LISTENER = ChatEventResubmitter.TARGET_LISTENER + "on(PartyMemberJoinedEvent)";

    private TargetEventPublication publication(String listenerId, int attempts) {
        TargetEventPublication p = mock(TargetEventPublication.class);
        given(p.getTargetIdentifier()).willReturn(PublicationTargetIdentifier.of(listenerId));
        given(p.getCompletionAttempts()).willReturn(attempts);
        given(p.getPublicationDate()).willReturn(T0);
        return p;
    }

    @Test
    void 채팅방_멤버십_리스너만_재발행한다() {
        String push = "team.codingforest.moyeota.chat.notification.application.ChatNotificationListener.onMessageSent(ChatMessageSentEvent)";
        Instant later = T0.plusSeconds(3600);

        assertThat(ChatEventResubmitter.shouldResubmit(publication(MEMBER_LISTENER, 1), later)).isTrue();
        assertThat(ChatEventResubmitter.shouldResubmit(publication(push, 1), later)).isFalse();
    }

    @Test
    void 백오프_시간이_안_지났으면_건너뛴다() {
        assertThat(ChatEventResubmitter.shouldResubmit(publication(MEMBER_LISTENER, 1), T0.plusSeconds(30))).isFalse();
    }

    @Test
    void dead_letter_이동이_재발행보다_먼저_실행되고_건수와_실행시간이_집계된다() {
        IncompleteEventPublications publications = mock(IncompleteEventPublications.class);
        ChatEventDeadLetters deadLetters = mock(ChatEventDeadLetters.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        given(deadLetters.moveExhausted(anyString(), anyInt())).willReturn(2);

        new ChatEventResubmitter(publications, deadLetters, registry).run();

        InOrder order = inOrder(deadLetters, publications);
        order.verify(deadLetters).moveExhausted(ChatEventResubmitter.TARGET_LISTENER, ChatEventRetryPolicy.MAX_ATTEMPTS);
        order.verify(publications).resubmitIncompletePublications(any(ResubmissionOptions.class));
        assertThat(registry.counter("chat.event.dead_letter").count()).isEqualTo(2.0);
        assertThat(registry.timer("chat.event.resubmit.duration").count()).isEqualTo(1);
    }
}