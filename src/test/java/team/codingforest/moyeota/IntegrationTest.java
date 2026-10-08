package team.codingforest.moyeota;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis·Postgres 같은 외부 서비스가 떠 있어야 도는 테스트.
 * Gradle 의 test 태스크는 이 태그를 제외하고, integrationTest 태스크만 포함한다.
 * 로컬에 서비스가 없으면 integrationTest 는 건너뛰고(SKIPPED), CI 에서는 서비스 컨테이너로 돈다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
public @interface IntegrationTest {
}
