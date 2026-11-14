package team.codingforest.moyeota.user.social;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** docker-compose 로컬 PostGIS에서 V42 데이터 보존과 V43 UNIQUE 제약을 확인한다. 운영 DB를 사용하지 않는다. */
class SocialLoginMigrationPostgresTest {
    @Test void 기존_소셜_회원_보존과_다중_연결_제약을_검증한다() {
        var source=new DriverManagerDataSource("jdbc:postgresql://localhost:5432/moyeota","moyeota","moyeota");
        var jdbc=new JdbcTemplate(source);
        String schema="social_v43_"+UUID.randomUUID().toString().replace("-","");
        try {
            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).target("42").load().migrate();
            jdbc.update("insert into "+schema+".social_user(user_id,social_type,social_id,created_at) values (77,'GOOGLE','old-google',CURRENT_TIMESTAMP)");
            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).target("43").load().migrate();
            assertThat(jdbc.queryForObject("select user_id from "+schema+".social_user where social_id='old-google'",Long.class)).isEqualTo(77L);
            assertThat(jdbc.queryForObject("select status from "+schema+".social_user where social_id='old-google'",String.class)).isEqualTo("ACTIVE");
            jdbc.update("insert into "+schema+".social_user(user_id,social_type,social_id,created_at) values (77,'APPLE','new-apple',CURRENT_TIMESTAMP)");
            assertThat(jdbc.queryForObject("select count(*) from "+schema+".social_user where user_id=77",Integer.class)).isEqualTo(2);
            assertThatThrownBy(() -> jdbc.update("insert into "+schema+".social_user(user_id,social_type,social_id,created_at) values (78,'GOOGLE','old-google',CURRENT_TIMESTAMP)")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into "+schema+".social_user(user_id,social_type,social_id,created_at) values (77,'APPLE','other-apple',CURRENT_TIMESTAMP)")).isInstanceOf(DataIntegrityViolationException.class);
        } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
    }
}
