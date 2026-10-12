package team.codingforest.moyeota.driver.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.driver.dto.DriverFcmToken;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DriverJpaRepository extends JpaRepository<DriverEntity, Long> {
    @Query("select d from DriverEntity d where d.userId = :userId")
    Optional<DriverEntity> findByUserId(@Param("userId") Long userId);

    @Query("""
                    select new team.codingforest.moyeota.driver.dto.DriverFcmToken(d.id, d.fcmToken)
                    from DriverEntity d where d.id in :ids and d.fcmToken is not null and d.fcmToken <> ''
            """)
    List<DriverFcmToken> findFcmTokens(@Param("ids")Collection<Long> ids);
}
