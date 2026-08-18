package team.codingforest.moyeota.driver.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DriverJpaRepository extends JpaRepository<DriverEntity, Long> {
    @Query("select d from DriverEntity d where d.userId = :userId")
    Optional<DriverEntity> findByUserId(@Param("userId") Long userId);
}
