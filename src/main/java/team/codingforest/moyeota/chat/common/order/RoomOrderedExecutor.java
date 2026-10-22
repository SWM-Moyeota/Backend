package team.codingforest.moyeota.chat.common.order;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 방 번호로 스레드를 골라 같은 방의 작업은 한 스레드에서 순서대로 처리함. 다른 방은 다른 스레드에서 동시에 처리
 */
@Component
public class RoomOrderedExecutor {

    private static final int LANES = 4;
    private static final int QUEUE_PER_LANE = 2_500;

    private final List<ThreadPoolTaskExecutor> lanes = new ArrayList<>();

    /**
     * 줄마다 큐에 쌓인 수를 chat.lane.queued 지표로 남김
     *
     * @param meterRegistry 지표 등록
     */
    public RoomOrderedExecutor(MeterRegistry meterRegistry) {
        for (int i = 0; i < LANES; i++) {
            ThreadPoolTaskExecutor lane = new ThreadPoolTaskExecutor();
            lane.setCorePoolSize(1);
            lane.setMaxPoolSize(1);
            lane.setQueueCapacity(QUEUE_PER_LANE);
            lane.setThreadNamePrefix("chat-lane-" + i + "-");
            lane.setWaitForTasksToCompleteOnShutdown(true);
            lane.setAwaitTerminationSeconds(10);
            lane.initialize();
            lanes.add(lane);

            Gauge.builder("chat.lane.queued", lane, l -> l.getThreadPoolExecutor().getQueue().size())
                    .tag("lane", String.valueOf(i))
                    .register(meterRegistry);
        }
    }

    /**
     * 방 id 로 고른 줄의 끝에 넣음. 줄이 가득 차면 TaskRejectedException 을 던짐
     *
     * @param chatRoomId 순서를 지킬 단위
     * @param task 실행할 작업
     */
    public void execute(Long chatRoomId, Runnable task) {
        lanes.get(Math.floorMod(chatRoomId, LANES)).execute(task);
    }

    @PreDestroy
    void shutdown() {
        lanes.forEach(lane -> lane.getThreadPoolExecutor().shutdown());
        lanes.forEach(ThreadPoolTaskExecutor::shutdown);
    }
}
