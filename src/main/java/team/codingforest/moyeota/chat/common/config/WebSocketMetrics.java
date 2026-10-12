package team.codingforest.moyeota.chat.common.config;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;

/**
 * WebSocket·STOMP 세션 통계를 Prometheus 지표로 노출함
 * Spring 은 이 통계를 30분마다 로그 문자열로만 남기고 Micrometer 로 보내지 않음
 */
@Component
@RequiredArgsConstructor
public class WebSocketMetrics implements MeterBinder {
    private final WebSocketMessageBrokerStats stats;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("chat.ws.sessions", stats,
                        s -> s.getWebSocketSessionStats().getWebSocketSessions())
                .register(registry);

        FunctionCounter.builder("chat.stomp.connected", stats,
                        s -> s.getStompSubProtocolStats().getTotalConnected())
                .register(registry);
        FunctionCounter.builder("chat.stomp.disconnect", stats,
                        s -> s.getStompSubProtocolStats().getTotalDisconnect())
                .register(registry);
        FunctionCounter.builder("chat.ws.transport.error", stats,
                        s -> s.getWebSocketSessionStats().getTransportErrorSessions())
                .register(registry);
        FunctionCounter.builder("chat.ws.no.message", stats,
                        s -> s.getWebSocketSessionStats().getNoMessagesReceivedSessions())
                .register(registry);
    }
}
