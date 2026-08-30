package team.codingforest.moyeota.report.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.report.domain.Report;
import team.codingforest.moyeota.report.domain.enums.ReportStatus;

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

    @Test
    void 신고_스냅샷은_저장_후_다시_조회해도_그대로_남는다() {
        // 사후 조사용 기록 - 왕복에서 한 필드라도 유실되면 증거가 사라진다
        Report saved = reports.save(Report.create(5L, 1L, 9L, 37.4979, 127.0276));

        Report reloaded = reports.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getReporterId()).isEqualTo(5L);
        assertThat(reloaded.getPartyId()).isEqualTo(1L);
        assertThat(reloaded.getDriverId()).isEqualTo(9L);
        assertThat(reloaded.getReporterLatitude()).isEqualTo(37.4979);
        assertThat(reloaded.getReporterLongitude()).isEqualTo(127.0276);
        assertThat(reloaded.getStatus()).isEqualTo(ReportStatus.REPORTED);
    }

    @Test
    void 신고_시각은_감사_필드가_자동으로_기록한다() {
        // reportedAt 컬럼 제거 후 createdAt(BaseTimeEntity)이 그 역할 - 감사 설정이 빠지면 null이 된다
        Report saved = reports.save(Report.create(5L, 1L, 9L, null, null));

        assertThat(repository.findById(saved.getId()).orElseThrow().getCreatedAt()).isNotNull();
    }

    @Test
    void 스냅샷이_전부_비어도_저장된다() {
        // 위급상황 최소 기록 - 신고자만으로도 행이 남아야 한다
        Report saved = reports.save(Report.create(5L, null, null, null, null));

        assertThat(reports.findById(saved.getId())).isPresent();
    }

    @Test
    void 통화_여부를_확정해도_행이_늘어나지_않고_상태만_바뀐다() {
        Report saved = reports.save(Report.create(5L, 1L, 9L, null, null));
        long before = repository.count();

        saved.confirmCall(true);
        reports.save(saved);

        assertThat(repository.count()).isEqualTo(before);   // UPDATE가 아니라 INSERT면 신고가 복제된다
        assertThat(reports.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.CALL_CONFIRMED);
    }

    @Test
    void 확정하지_않은_신고는_REPORTED로_남는다() {
        // 복귀하지 못한 신고 - 이 잔류 상태 자체가 가장 강한 위험 신호 데이터
        Report saved = reports.save(Report.create(5L, 1L, 9L, null, null));

        assertThat(reports.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.REPORTED);
    }
}
