package team.codingforest.moyeota.report.dto;

public record ReportResponse(Long reportId) {

    public static ReportResponse of(Long reportId) {
        return new ReportResponse(reportId);
    }
}