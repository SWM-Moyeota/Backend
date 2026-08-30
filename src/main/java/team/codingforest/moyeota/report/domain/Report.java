package team.codingforest.moyeota.report.domain;

import lombok.Getter;
import team.codingforest.moyeota.report.domain.enums.ReportStatus;

import java.time.Instant;

@Getter
public class Report {
    private final Long id;
    private final Long reporterId;
    private final Long partyId;
    private final Long driverId;
    private final Double reporterLatitude;
    private final Double reporterLongitude;
    private final Instant reportedAt;
    private ReportStatus status;

    private Report(Long id, Long reporterId, Long partyId, Long driverId,
                   Double reporterLatitude, Double reporterLongitude, Instant reportedAt, ReportStatus status) {
        this.id = id;
        this.reporterId = reporterId;
        this.partyId = partyId;
        this.driverId = driverId;
        this.reporterLatitude = reporterLatitude;
        this.reporterLongitude = reporterLongitude;
        this.reportedAt = reportedAt;
        this.status = status;
    }

    /**
     *  긴급 신고 사전 기록 - 다이얼 전환 직전에 생성되므로 신고자 외엔 아무것도 필수가 아니다
     */
    public static Report create(Long reporterId, Long partyId, Long driverId,
                                Double reporterLatitude, Double reporterLongitude, Instant reportedAt) {
        if(reporterId == null) throw new IllegalArgumentException("신고자 정보가 없습니다.");

        return new Report(null, reporterId, partyId, driverId,
                reporterLatitude, reporterLongitude, reportedAt, ReportStatus.REPORTED);
    }

    /**
     *  복귀 후 통화 여부 보강 - 미확정(REPORTED) 상태에서 한 번만 가능
     */
    public void confirmCall(boolean called) {
        if(status != ReportStatus.REPORTED) throw new IllegalArgumentException("이미 통화 여부가 확정된 신고입니다.");

        this.status = called ? ReportStatus.CALL_CONFIRMED : ReportStatus.CALL_NOT_MADE;
    }

    public static Report restore(Long id, Long reporterId, Long partyId, Long driverId,
                                 Double reporterLatitude, Double reporterLongitude, Instant reportedAt, ReportStatus status) {
        return new Report(id, reporterId, partyId, driverId,
                reporterLatitude, reporterLongitude, reportedAt, status);
    }
}