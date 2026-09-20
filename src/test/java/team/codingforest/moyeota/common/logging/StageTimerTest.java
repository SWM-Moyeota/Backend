package team.codingforest.moyeota.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageTimerTest {

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(StageTimer.class)).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(StageTimer.class)).detachAppender(appender);
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().collect(Collectors.toMap((KeyValuePair p) -> p.key, p -> p.value));
    }

    @Test
    void 결과를_그대로_돌려주고_단계_이름과_소요_시간을_남긴다() {
        String result = StageTimer.time("kakao.place-search", () -> "ok");

        assertThat(result).isEqualTo("ok");
        Map<String, Object> f = fields(appender.list.get(0));
        assertThat(f).containsEntry("stage", "kakao.place-search")
                .containsEntry("success", true)
                .containsKey("durationMs");
    }

    @Test
    void 예외가_나도_실패로_기록한_뒤_그대로_던진다() {
        assertThatThrownBy(() -> StageTimer.time("naver.directions", () -> { throw new IllegalStateException("timeout"); }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(fields(appender.list.get(0))).containsEntry("success", false);
    }
}
