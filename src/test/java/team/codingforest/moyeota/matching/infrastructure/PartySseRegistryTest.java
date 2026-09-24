package team.codingforest.moyeota.matching.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  방별 SSE 연결 장부.
 *  HTTP 응답이 붙지 않은 SseEmitter 는 send 를 버퍼에 쌓고 complete 도 콜백을 부르지 않는다 - 그래서 여기서는
 *  등록·닫기·빈 방 처리만 본다. "send 실패 → 장부에서 제거" 는 실제 응답이 있어야 하므로 통합 테스트 영역이다.
 */
class PartySseRegistryTest {
    private static final Long 방 = 42L;
    private static final Long 다른방 = 43L;

    private PartySseRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PartySseRegistry();
    }

    @Test
    void 구독하면_그_방의_연결로_등록된다() {
        registry.subscribe(방);
        registry.subscribe(방);
        registry.subscribe(다른방);

        assertThat(registry.connections()).isEqualTo(3);
    }

    @Test
    void 구독마다_새_연결을_돌려준다() {
        SseEmitter a = registry.subscribe(방);
        SseEmitter b = registry.subscribe(방);

        assertThat(a).isNotSameAs(b);
    }

    @Test
    void 방을_닫으면_그_방의_연결만_전부_빠진다() {
        registry.subscribe(방);
        registry.subscribe(방);
        registry.subscribe(다른방);

        registry.closeAll(방);

        assertThat(registry.connections()).as("다른 방 연결은 그대로").isEqualTo(1);
    }

    @Test
    void 닫은_방에_다시_구독할_수_있다() {
        registry.subscribe(방);
        registry.closeAll(방);

        registry.subscribe(방);

        assertThat(registry.connections()).isEqualTo(1);
    }

    @Test
    void 연결_없는_방을_닫아도_아무_일도_없다() {
        registry.closeAll(방);

        assertThat(registry.connections()).isZero();
    }

    @Test
    void 연결_없는_방에_알려도_예외가_없다() {
        registry.notify(방, "changed");
        registry.heartbeat();

        assertThat(registry.connections()).isZero();
    }

    @Test
    void 알림과_heartbeat_는_살아_있는_연결을_지우지_않는다() {
        registry.subscribe(방);
        registry.subscribe(다른방);

        registry.notify(방, "changed");
        registry.heartbeat();

        assertThat(registry.connections()).isEqualTo(2);
    }
}
