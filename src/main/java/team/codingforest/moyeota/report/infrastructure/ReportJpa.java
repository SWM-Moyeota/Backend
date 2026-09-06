package team.codingforest.moyeota.report.infrastructure;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.report.domain.exception.ReportErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.report.domain.Report;
import team.codingforest.moyeota.report.domain.Reports;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReportJpa implements Reports {
    private final ReportJpaRepository delegate;

    @Override
    public Report save(Report report) {
        if(report.getId() != null) {
            ReportEntity entity = delegate.findById(report.getId())
                    .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));

            entity.update(report);
            delegate.save(entity);
            return entity.toDomain();
        }

        ReportEntity entity = ReportEntity.from(report);
        delegate.save(entity);
        return entity.toDomain();
    }

    @Override
    public Optional<Report> findById(Long id) {
        return delegate.findById(id).map(ReportEntity::toDomain);
    }

    @Override
    public Optional<Report> findLatestByReporterId(Long reporterId) {
        return delegate.findTopByReporterIdOrderByIdDesc(reporterId).map(ReportEntity::toDomain);
    }
}