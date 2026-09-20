package team.codingforest.moyeota.chat.infrastructure.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"chat.search.enabled=true", "chat.search.initial-delay=86400000"})
class ChatSearchContextTest {
    @Autowired ApplicationContext context;
    @Test void 검색_활성화시_클라이언트와_전용_스케줄러가_구성된다() {
        assertThat(context.getBean(ElasticsearchMessageIndex.class)).isNotNull();
        assertThat(context.getBean(SearchOutboxWorker.class)).isNotNull();
        assertThat(context.getBean("chatSearchScheduler", TaskScheduler.class))
                .isNotSameAs(context.getBean("heartbeatScheduler", TaskScheduler.class));
    }
}
