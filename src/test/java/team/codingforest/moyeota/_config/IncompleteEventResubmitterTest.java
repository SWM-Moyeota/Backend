package team.codingforest.moyeota._config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  큐에서 거절되거나 실패해 event_publication 에 남은 이벤트를 다시 큐에 넣는 스케줄러.
 *  5초마다 돌므로 "아직 메모리 큐에서 차례를 기다리는 것"을 또 넣지 않는 것이 핵심이다 -
 *  DB 의 미완료 행만 봐서는 버려진 것과 대기 중인 것을 구분할 수 없다.
 */
class IncompleteEventResubmitterTest {
    private final RecordingPublications publications = new RecordingPublications();
    private final CountDownLatch release = new CountDownLatch(1);
    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void 실행기_정리() {
        release.countDown();
        if (executor != null) executor.shutdown();
    }

    // ───────────────────────── 언제 도는가 ─────────────────────────

    @Test
    void 큐에_일이_많이_남아_있으면_아무것도_하지_않는다() {
        IncompleteEventResubmitter resubmitter = resubmitterWith(queued(101), 5000);

        resubmitter.resubmit();

        assertThat(publications.calls)
                .as("메모리 큐가 먼저 빠져야 DB 에 남은 것이 버려진 것인지 알 수 있다 - 지금 넣으면 대기 중인 것을 또 넣는다")
                .isZero();
    }

    @Test
    void 큐가_거의_비면_돈다() {
        IncompleteEventResubmitter resubmitter = resubmitterWith(queued(100), 5000);

        resubmitter.resubmit();

        assertThat(publications.calls).isEqualTo(1);
    }

    // ───────────────────────── 무엇을 집는가 ─────────────────────────

    @Test
    void 발행된_지_30초가_지난_미완료만_집는다() {
        resubmitterWith(queued(0), 5000).resubmit();

        assertThat(publications.accepts(published(ago(31)))).isTrue();
        assertThat(publications.accepts(published(ago(29))))
                .as("방금 발행돼 이제 막 큐에 들어가는 중일 수 있다")
                .isFalse();
    }

    @Test
    void 방금_다시_넣은_것은_30초_동안_또_집지_않는다() {
        resubmitterWith(queued(0), 5000).resubmit();

        assertThat(publications.accepts(resubmitted(ago(300), ago(10))))
                .as("5초마다 돌기 때문에 이게 없으면 처리 중인 이벤트를 20초 만에 4번 더 넣고 시도 횟수를 다 써 버린다")
                .isFalse();
        assertThat(publications.accepts(resubmitted(ago(300), ago(31)))).isTrue();
    }

    @Test
    void 처음_저장될_때_재제출_시각이_발행_시각으로_채워져_있어도_30초_뒤면_집는다() {
        resubmitterWith(queued(0), 5000).resubmit();

        // Modulith 는 발행 기록을 처음 넣을 때 마지막 재제출 시각 = 발행 시각으로 저장한다.
        // 다시 넣은 것을 쉬게 하는 시간이 30초보다 길면, 한 번도 다시 넣지 않은 이벤트가 그만큼 더 기다리게 된다
        Instant publishedAt = ago(31);
        assertThat(publications.accepts(new FakePublication(publishedAt, publishedAt, 1))).isTrue();
    }

    @Test
    void 한_번도_다시_넣은_적_없는_것은_집는다() {
        resubmitterWith(queued(0), 5000).resubmit();

        assertThat(publications.accepts(new FakePublication(ago(300), null, 1))).isTrue();
    }

    @Test
    void 시도가_5회에_이른_것은_포기한다() {
        resubmitterWith(queued(0), 5000).resubmit();

        assertThat(publications.accepts(new FakePublication(ago(300), ago(300), 4))).isTrue();
        assertThat(publications.accepts(new FakePublication(ago(300), ago(300), 5)))
                .as("매번 실패하는 이벤트를 영원히 돌리지 않는다")
                .isFalse();
    }

    // ───────────────────────── 얼마나 넣는가 ─────────────────────────

    @Test
    void 큐_빈_자리의_절반까지만_넣는다() {
        resubmitterWith(queued(0), 10).resubmit();          // 빈 자리 10 → 5건

        long accepted = eligible(8).stream().filter(publications::accepts).count();

        assertThat(accepted)
                .as("나머지 절반은 새로 들어오는 이벤트 자리다 - 다 채우면 재제출이 새 거절을 만든다")
                .isEqualTo(5);
    }

    @Test
    void 조건에_안_맞는_것은_빈_자리를_쓰지_않는다() {
        resubmitterWith(queued(0), 10).resubmit();          // 5건까지

        List<EventPublication> mixed = new ArrayList<>();
        for (int i = 0; i < 20; i++) mixed.add(published(ago(5)));      // 너무 새것 - 건너뛴다
        mixed.addAll(eligible(5));

        long accepted = mixed.stream().filter(publications::accepts).count();

        assertThat(accepted).as("앞에 대상이 아닌 것이 많아도 뒤의 대상 5건을 다 집는다").isEqualTo(5);
    }

    @Test
    void 큐에_자리가_없으면_한_건도_넣지_않는다() {
        resubmitterWith(queued(1), 1).resubmit();           // 용량 1 이 차 있다 → 빈 자리 0

        assertThat(eligible(3).stream().filter(publications::accepts).count()).isZero();
    }

    // ───────────────────────── 준비 ─────────────────────────

    /** 스레드 1개를 붙잡아 두고 큐에 n 개를 쌓아 둔 실행기의 "대기 중인 일 수" */
    private int queued(int n) { return n; }

    private IncompleteEventResubmitter resubmitterWith(int queuedTasks, int queueCapacity) {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        if (queuedTasks > 0) {
            CountDownLatch running = new CountDownLatch(1);
            executor.execute(() -> { running.countDown(); await(release); });   // 하나뿐인 스레드를 붙잡는다
            await(running);
            for (int i = 0; i < queuedTasks; i++) executor.execute(() -> { });   // 나머지는 큐에 쌓인다
        }
        return new IncompleteEventResubmitter(publications, executor);
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static Instant ago(int seconds) { return Instant.now().minus(Duration.ofSeconds(seconds)); }

    /** 발행 후 한 번도 다시 넣지 않은 것 - Modulith 는 처음 저장할 때 시도 1, 재제출 시각 = 발행 시각으로 넣는다 */
    private static EventPublication published(Instant at) { return new FakePublication(at, at, 1); }

    private static EventPublication resubmitted(Instant publishedAt, Instant resubmittedAt) { return new FakePublication(publishedAt, resubmittedAt, 2); }

    private static List<EventPublication> eligible(int n) {
        List<EventPublication> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(published(ago(300)));
        return list;
    }

    /** 재제출기가 넘긴 조건(Predicate)을 붙잡아 둔다 - 실제 재제출(리스너 재호출)은 Modulith 의 일이다 */
    static class RecordingPublications implements IncompleteEventPublications {
        int calls;
        private Predicate<EventPublication> filter;

        @Override
        public void resubmitIncompletePublications(Predicate<EventPublication> filter) {
            calls++;
            this.filter = filter;
        }

        boolean accepts(EventPublication publication) { return filter.test(publication); }

        @Override public void resubmitIncompletePublicationsOlderThan(Duration duration) { throw new UnsupportedOperationException(); }
        @Override public void resubmitIncompletePublications(ResubmissionOptions options) { throw new UnsupportedOperationException(); }
    }

    record FakePublication(Instant publicationDate, Instant lastResubmissionDate, int completionAttempts) implements EventPublication {
        @Override public UUID getIdentifier() { return UUID.randomUUID(); }
        @Override public Object getEvent() { return "event"; }
        @Override public Instant getPublicationDate() { return publicationDate; }
        @Override public Optional<Instant> getCompletionDate() { return Optional.empty(); }
        @Override public Status getStatus() { return Status.PUBLISHED; }
        @Override public Instant getLastResubmissionDate() { return lastResubmissionDate; }
        @Override public int getCompletionAttempts() { return completionAttempts; }
    }
}
