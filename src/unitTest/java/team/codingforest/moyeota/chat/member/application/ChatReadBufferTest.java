package team.codingforest.moyeota.chat.member.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import team.codingforest.moyeota.chat.member.domain.PendingReads;
import team.codingforest.moyeota.chat.member.domain.ReadPosition;
import team.codingforest.moyeota.chat.member.domain.ReadPositions;
import team.codingforest.moyeota.chat.member.infrastructure.PendingReadsMemory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ChatReadBufferTest {

    private ReadPositions readPositions;
    private ChatReadBuffer buffer;

    @BeforeEach
    void setUp() {
        readPositions = mock(ReadPositions.class);
        buffer = new ChatReadBuffer(new PendingReadsMemory(), readPositions, new SimpleMeterRegistry());   // Redis 자리에 같은 규칙의 메모리 구현을 넣음
    }

    @SuppressWarnings("unchecked")
    private List<ReadPosition> flushed() {
        ArgumentCaptor<List<ReadPosition>> captor = ArgumentCaptor.forClass(List.class);
        verify(readPositions).advance(captor.capture());
        return captor.getValue();
    }

    @Test
    void 같은_방은_가장_큰_값_하나만_보낸다() {
        buffer.record(7L, 1L, 5L);
        buffer.record(7L, 1L, 3L);
        buffer.record(7L, 1L, 9L);

        buffer.flush();

        assertThat(flushed()).containsExactly(new ReadPosition(1L, 7L, 9L));
    }

    @Test
    void 비어_있으면_DB에_가지_않는다() {
        buffer.flush();

        verify(readPositions, never()).advance(anyList());
    }

    @Test
    void 보낸_뒤에는_비워진다() {
        buffer.record(7L, 1L, 5L);
        buffer.flush();
        buffer.flush();

        verify(readPositions, times(1)).advance(anyList());
    }

    @Test
    void 그_사람_것만_먼저_보낼_수_있다() {
        buffer.record(7L, 1L, 5L);
        buffer.record(8L, 1L, 6L);

        buffer.flushUser(7L);

        assertThat(flushed()).containsExactly(new ReadPosition(1L, 7L, 5L));
    }

    @Test
    void 반영에_실패하면_다음_주기에_다시_보낸다() {
        given(readPositions.advance(anyList())).willThrow(new RuntimeException("DB 장애")).willReturn(1);
        buffer.record(7L, 1L, 5L);

        buffer.flush();
        buffer.flush();

        verify(readPositions, times(2)).advance(List.of(new ReadPosition(1L, 7L, 5L)));
    }

    @Test
    void 한_사람의_방이_너무_많으면_쌓지_않고_바로_쓴다() {
        for (long room = 0; room < 50; room++) {
            buffer.record(7L, room, 1L);
        }

        buffer.record(7L, 999L, 1L);

        verify(readPositions).advance(List.of(new ReadPosition(999L, 7L, 1L)));
    }

    @Test
    void 방이_많아도_이미_있는_방은_계속_모은다() {
        for (long room = 0; room < 50; room++) {
            buffer.record(7L, room, 1L);
        }

        buffer.record(7L, 0L, 2L);

        verify(readPositions, never()).advance(anyList());
    }

    @Test
    void 상한에서_DB가_계속_실패해도_재귀하지_않는다() {
        given(readPositions.advance(anyList())).willThrow(new RuntimeException("DB 장애"));
        for (long room = 0; room < 50; room++) {
            buffer.record(7L, room, 1L);
        }

        assertThatCode(() -> buffer.record(7L, 999L, 1L)).doesNotThrowAnyException();
    }

    @Nested
    class Redis가_실패하면 {

        private PendingReads broken;
        private ChatReadBuffer buffer;

        @BeforeEach
        void setUp() {
            broken = mock(PendingReads.class);
            given(broken.record(any(), anyInt())).willThrow(new RedisConnectionFailureException("Redis 장애"));
            buffer = new ChatReadBuffer(broken, readPositions, new SimpleMeterRegistry());
        }

        @Test
        void DB에_바로_쓰지_않고_서버_메모리에_모은다() {
            buffer.record(7L, 1L, 5L);

            verify(readPositions, never()).advance(anyList());
        }

        @Test
        void 메모리에_모은_것은_다음_주기에_쓴다() {
            buffer.record(7L, 1L, 5L);
            buffer.record(7L, 1L, 6L);

            buffer.flush();

            assertThat(flushed()).containsExactly(new ReadPosition(1L, 7L, 6L));
        }

        @Test
        void 잠시_Redis를_부르지_않는다() {
            buffer.record(7L, 1L, 5L);
            buffer.record(7L, 1L, 6L);
            buffer.flush();

            verify(broken, times(1)).record(any(), anyInt());
            verify(broken, never()).popUsers(anyInt());
        }

        @Test
        void 목록_직전에는_메모리에_모은_것도_바로_쓴다() {
            buffer.record(7L, 1L, 5L);

            buffer.flushUser(7L);

            assertThat(flushed()).containsExactly(new ReadPosition(1L, 7L, 5L));
            verify(broken, never()).take(any());
        }

        @Test
        void DB도_실패하면_메모리에_남겨_다음_주기에_다시_쓴다() {
            given(readPositions.advance(anyList())).willThrow(new RuntimeException("DB 장애")).willReturn(1);
            buffer.record(7L, 1L, 5L);

            buffer.flush();
            buffer.flush();

            verify(readPositions, times(2)).advance(List.of(new ReadPosition(1L, 7L, 5L)));
        }

        @Test
        void 서버가_꺼질_때_메모리에_모은_것을_쓴다() {
            buffer.record(7L, 1L, 5L);

            buffer.flushOnShutdown();

            assertThat(flushed()).containsExactly(new ReadPosition(1L, 7L, 5L));
        }

        @Test
        void DB가_계속_실패하면_이번_주기는_한_번만_시도하고_멈춘다() {
            given(readPositions.advance(anyList())).willThrow(new RuntimeException("DB 장애"));
            for (long user = 0; user < 500; user++) {
                buffer.record(user, 1L, 1L);
            }

            buffer.flush();

            verify(readPositions, times(1)).advance(anyList());
        }
    }
}