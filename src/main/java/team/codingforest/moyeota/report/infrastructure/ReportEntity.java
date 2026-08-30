package team.codingforest.moyeota.report.infrastructure;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.report.domain.Report;
import team.codingforest.moyeota.report.domain.enums.ReportStatus;

import java.time.Instant;

@Table(name = "driver_report")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportEntity extends BaseTimeEntity {

    @Column(nullable = false)
    private Long reporterId;

    private Long partyId;
    private Long driverId;
    private Double reporterLatitude;
    private Double reporterLongitude;

    @Column(nullable = false)
    private Instant reportedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    private ReportEntity(Long reporterId, Long partyId, Long driverId,
                         Double reporterLatitude, Double reporterLongitude, Instant reportedAt, ReportStatus status) {
        this.reporterId = reporterId;
        this.partyId = partyId;
        this.driverId = driverId;
        this.reporterLatitude = reporterLatitude;
        this.reporterLongitude = reporterLongitude;
        this.reportedAt = reportedAt;
        this.status = status;
    }

    public static ReportEntity from(Report report) {
        return new ReportEntity(report.getReporterId(), report.getPartyId(), report.getDriverId(),
                report.getReporterLatitude(), report.getReporterLongitude(),
                report.getReportedAt(), report.getStatus());
    }

    public Report toDomain() {
        return Report.restore(getId(), reporterId, partyId, driverId,
                reporterLatitude, reporterLongitude, reportedAt, status);
    }

    public void update(Report report) {
        this.status = report.getStatus();
    }
}