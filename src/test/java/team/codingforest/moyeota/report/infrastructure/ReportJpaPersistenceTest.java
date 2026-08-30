package team.codingforest.moyeota.report.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.report.domain.Report;
import team.codingforest.moyeota.report.domain.enums.ReportStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({ReportJpa.class, JpaAuditingConfig.class})
class ReportJpaPersistenceTest {
    private final ReportJpa reports;
    private final ReportJpaRepository repository;

    @Autowired
    ReportJpaPersistenceTest(ReportJpa reports, ReportJpaRepository repository) {
        this.reports = reports;
        this.repository = repository;
    }

    private static final Instant 신고시각 = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void 신고_스냅샷은_저장_후_다시_조회해도_그대로_남는다() {
        // 사후 조사용 기록 - 왕복에서 한 필드라도 유실되면 증거가 사라진다
        Report saved = reports.save(Report.create(5L, 1L, 9L, 37.4979, 127.0276, 신고시각));

        Report reloaded = reports.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getReporterId()).isEqualTo(5L);
        assertThat(reloaded.getPartyId()).isEqualTo(1L);
        assertThat(reloaded.getDriverId()).isEqualTo(9L);
        assertThat(reloaded.getReporterLatitude()).isEqualTo(37.4979);
        assertThat(reloaded.getReporterLongitude()).isEqualTo(127.0276);
        assertThat(reloaded.getReportedAt()).isEqualTo(신고시각);
        assertThat(reloaded.getStatus()).isEqualTo(ReportStatus.REPORTED);
    }

    @Test
    void 스냅샷이_전부_비어도_저장된다() {
        // 위급상황 최소 기록 - 신고자와 시각만으로도 행이 남아야 한다
        Report saved = reports.save(Report.create(5L, null, null, null, null, 신고시각));

        assertThat(reports.findById(saved.getId())).isPresent();
    }

    @Test
    void 통화_여부를_확정해도_행이_늘어나지_않고_상태만_바뀐다() {
        Report saved = reports.save(Report.create(5L, 1L, 9L, null, null, 신고시각));
        long before = repository.count();

        saved.confirmCall(true);
        reports.save(saved);

        assertThat(repository.count()).isEqualTo(before);   // UPDATE가 아니라 INSERT면 신고가 복제된다
        assertThat(reports.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.CALL_CONFIRMED);
    }

    @Test
    void 확정하지_않은_신고는_REPORTED로_남는다() {
        // 복귀하지 못한 신고 - 이 잔류 상태 자체가 가장 강한 위험 신호 데이터
        Report saved = reports.save(Report.create(5L, 1L, 9L, null, null, 신고시각));

        assertThat(reports.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.REPORTED);
    }
}
