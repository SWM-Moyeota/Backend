package team.codingforest.moyeota.report.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportJpaRepository extends JpaRepository<ReportEntity, Long> {
    java.util.Optional<ReportEntity> findTopByReporterIdOrderByIdDesc(Long reporterId);
}