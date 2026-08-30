package team.codingforest.moyeota.report.infrastructure;

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
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다."));

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
}