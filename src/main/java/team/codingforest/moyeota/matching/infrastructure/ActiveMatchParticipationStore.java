package team.codingforest.moyeota.matching.infrastructure;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.PartyMember;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;

import javax.sql.DataSource;
import java.util.Set;
import java.util.TreeSet;

@Component
public class ActiveMatchParticipationStore {
    private final JdbcTemplate jdbc;

    public ActiveMatchParticipationStore(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    /** PartyJpa.save와 같은 트랜잭션. INSERT 충돌 시 파티/이벤트 저장까지 함께 롤백한다. */
    public void synchronize(Party party) {
        Set<Long> desired = new TreeSet<>();
        if (party.getStatus().isOngoing()) {
            party.getMembers().stream().map(PartyMember::getMemberId).forEach(desired::add);
        }
        Set<Long> existing = new TreeSet<>(jdbc.queryForList(
                "select user_id from active_match_participation where party_id = ? order by user_id",
                Long.class, party.getId()));
        for (Long userId : existing) {
            if (!desired.contains(userId)) {
                jdbc.update("delete from active_match_participation where user_id = ? and party_id = ?", userId, party.getId());
            }
        }
        for (Long userId : desired) {
            if (!existing.contains(userId)) {
                try {
                    // save/merge로 기존 소유자를 덮어쓰면 안 된다. PK가 최종 방어선이다.
                    jdbc.update("insert into active_match_participation(user_id, party_id) values (?, ?)", userId, party.getId());
                } catch (DuplicateKeyException e) {
                    throw new BusinessException(MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
                }
            }
        }
    }
}
