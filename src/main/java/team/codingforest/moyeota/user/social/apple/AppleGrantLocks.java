package team.codingforest.moyeota.user.social.apple;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

/** 동일 Apple 계정의 교환·활성화·철회만 직렬화한다. 호출 트랜잭션 종료까지 잠금 유지. */
@Repository
@RequiredArgsConstructor
public class AppleGrantLocks {
    private final JdbcTemplate jdbc;
    @Transactional(propagation=Propagation.MANDATORY)
    public void lock(String subject) {
        jdbc.update("insert into apple_grant_lock(subject) values (?) on conflict do nothing",subject);
        jdbc.queryForObject("select subject from apple_grant_lock where subject=? for update",String.class,subject);
    }
}
