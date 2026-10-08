package team.codingforest.moyeota;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 *  마이그레이션(V1..Vn)으로 만든 Postgres 스키마가 엔티티와 일치하는지 - 배포에서 validate 가 터지기 전에 여기서 잡는다.
 *  로컬 docker Postgres(5432) 또는 CI 의 postgres 서비스가 필요하다. 기본(H2) 프로필 테스트와는 별개.
 *  엔티티를 바꿨는데 이 테스트가 실패하면 답은 "새 V{n} 마이그레이션 추가" 이지 이 테스트 수정이 아니다.
 */
@SpringBootTest(properties = {"spring.profiles.active=dev", "spring.jpa.hibernate.ddl-auto=validate"})
class FlywayMigrationTest {

    @Test
    void 마이그레이션_적용_후_엔티티와_스키마가_일치한다() {
        // 컨텍스트가 뜨면 Flyway migrate → Hibernate validate 가 모두 통과한 것
    }
}
