package team.codingforest.moyeota.common;

import team.codingforest.moyeota.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.ReflectionUtils;
import team.codingforest.moyeota._config.AsyncConfig;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  @Async("이름") 은 문자열이라 컴파일러가 못 잡는다. 그 이름의 실행기 빈이 없어도 앱은 정상 기동하고,
 *  비동기 메서드가 처음 불리는 순간에야 NoSuchBeanDefinitionException 으로 터진다 - 실시간 전파가 통째로 사라진다.
 *  실행기 빈을 지우거나 이름을 바꿀 때 여기서 걸리게 한다.
 */
@IntegrationTest
@SpringBootTest
class AsyncExecutorContextTest {
    private static final String BASE_PACKAGE = "team.codingforest.moyeota";

    @Autowired ApplicationContext ctx;

    @Test
    void 코드가_이름으로_지정한_실행기는_전부_빈으로_떠_있다() {
        Set<String> names = asyncExecutorNamesInUse();

        assertThat(names).allSatisfy(name ->
                assertThat(ctx.containsBean(name) && ctx.getBean(name) instanceof Executor)
                        .as("@Async(\"%s\") 를 쓰는 곳이 있는데 그 이름의 Executor 빈이 없다", name)
                        .isTrue());
    }

    @Test
    void 이름_없는_Async_는_taskExecutor_가_받는다() {
        // WebSocket 설정이 TaskExecutor 빈을 여러 개 만든다 - @Primary 가 빠지면 기본 실행기를 못 골라 무제한 스레드(SimpleAsyncTaskExecutor)로 떨어진다
        assertThat(ctx.getBean(TaskExecutor.class)).isSameAs(ctx.getBean("taskExecutor"));
    }

    @Test
    void 실시간_실행기_상수와_빈_이름이_일치한다() {
        assertThat(ctx.containsBean(AsyncConfig.REALTIME_EXECUTOR)).isTrue();
    }

    /** 우리 패키지의 빈 중 클래스나 메서드에 @Async("이름") 을 붙인 것들의 이름 */
    private Set<String> asyncExecutorNamesInUse() {
        Set<String> names = new TreeSet<>();
        for (String beanName : ctx.getBeanDefinitionNames()) {
            Class<?> type = ctx.getType(beanName);
            if (type == null) continue;
            Class<?> target = AopUtils.isAopProxy(ctx.getBean(beanName)) ? AopUtils.getTargetClass(ctx.getBean(beanName)) : type;
            if (!target.getName().startsWith(BASE_PACKAGE)) continue;

            add(names, AnnotatedElementUtils.findMergedAnnotation(target, Async.class));
            ReflectionUtils.doWithMethods(target, m -> add(names, AnnotatedElementUtils.findMergedAnnotation(m, Async.class)));
        }
        return names;
    }

    private static void add(Set<String> names, Async async) {
        if (async != null && !async.value().isBlank()) names.add(async.value());
    }
}
