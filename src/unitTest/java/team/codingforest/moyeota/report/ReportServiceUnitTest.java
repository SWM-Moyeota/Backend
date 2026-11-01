package team.codingforest.moyeota.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartySummary;
import team.codingforest.moyeota.report.domain.Report;
import team.codingforest.moyeota.report.domain.ReportStatus;
import team.codingforest.moyeota.report.domain.Reports;
import team.codingforest.moyeota.report.exception.ReportErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReportServiceUnitTest {
    private static final Long REPORTER_ID = 1L;
    private static final Long PARTY_ID = 12L;
    private static final Long DRIVER_ID = 9L;
    private static final Long REPORT_ID = 100L;
    private static final Double LATITUDE = 37.4979;
    private static final Double LONGITUDE = 127.0276;

    @Mock
    private Reports reports;
    @Mock
    private PartyAccess partyAccess;
    @InjectMocks
    private ReportService service;
    @Captor
    private ArgumentCaptor<Report> reportCaptor;

    private static PartySummary summary(Long driverId) {
        return new PartySummary(PARTY_ID, 37.4, 127.0, 37.5, 127.1, "출발지", "도착지", 3, 10000, 20, driverId);
    }

    private static Report savedReport() {
        return Report.load(REPORT_ID, REPORTER_ID, PARTY_ID, DRIVER_ID, LATITUDE, LONGITUDE, ReportStatus.REPORTED);
    }

    private static Report reportOf(ReportStatus status) {
        return Report.load(REPORT_ID, REPORTER_ID, PARTY_ID, DRIVER_ID, LATITUDE, LONGITUDE, status);
    }

    @DisplayName("report - 운행 중 승객이면 기사 ID 와 위치까지 저장하고 신고 ID 를 반환")
    @Test
    void report() {

        /*
         * settings
         */
        doReturn(true).when(partyAccess).isRidingMember(eq(PARTY_ID), eq(REPORTER_ID));
        doReturn(Optional.of(summary(DRIVER_ID))).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(savedReport()).when(reports).save(any(Report.class));

        /*
         * test
         */
        Long reportId = service.report(REPORTER_ID, PARTY_ID, LATITUDE, LONGITUDE);

        /*
         * verify
         */
        assertThat(reportId).isEqualTo(REPORT_ID);
        verify(reports).save(reportCaptor.capture());
        Report created = reportCaptor.getValue();
        assertThat(created.getId()).isNull();
        assertThat(created.getReporterId()).isEqualTo(REPORTER_ID);
        assertThat(created.getPartyId()).isEqualTo(PARTY_ID);
        assertThat(created.getDriverId()).isEqualTo(DRIVER_ID);
        assertThat(created.getReporterLatitude()).isEqualTo(LATITUDE);
        assertThat(created.getReporterLongitude()).isEqualTo(LONGITUDE);
        assertThat(created.getStatus()).isEqualTo(ReportStatus.REPORTED);
    }

    @DisplayName("report - 방 스냅샷이 없어도 기사 ID 없이 저장")
    @Test
    void report_noSummary() {

        /*
         * settings
         */
        doReturn(true).when(partyAccess).isRidingMember(eq(PARTY_ID), eq(REPORTER_ID));
        doReturn(Optional.empty()).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(savedReport()).when(reports).save(any(Report.class));

        /*
         * test
         */
        Long reportId = service.report(REPORTER_ID, PARTY_ID, LATITUDE, LONGITUDE);

        /*
         * verify
         */
        assertThat(reportId).isEqualTo(REPORT_ID);
        verify(reports).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getDriverId()).isNull();
    }

    @DisplayName("report - 스냅샷에 기사가 없어도 기사 ID 없이 저장")
    @Test
    void report_noDriver() {

        /*
         * settings
         */
        doReturn(true).when(partyAccess).isRidingMember(eq(PARTY_ID), eq(REPORTER_ID));
        doReturn(Optional.of(summary(null))).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(savedReport()).when(reports).save(any(Report.class));

        /*
         * test
         */
        Long reportId = service.report(REPORTER_ID, PARTY_ID, LATITUDE, LONGITUDE);

        /*
         * verify
         */
        assertThat(reportId).isEqualTo(REPORT_ID);
        verify(reports).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getDriverId()).isNull();
    }

    @DisplayName("report - 위치가 없어도 저장")
    @Test
    void report_noLocation() {

        /*
         * settings
         */
        doReturn(true).when(partyAccess).isRidingMember(eq(PARTY_ID), eq(REPORTER_ID));
        doReturn(Optional.of(summary(DRIVER_ID))).when(partyAccess).findSummary(eq(PARTY_ID));
        doReturn(savedReport()).when(reports).save(any(Report.class));

        /*
         * test
         */
        Long reportId = service.report(REPORTER_ID, PARTY_ID, null, null);

        /*
         * verify
         */
        assertThat(reportId).isEqualTo(REPORT_ID);
        verify(reports).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getReporterLatitude()).isNull();
        assertThat(reportCaptor.getValue().getReporterLongitude()).isNull();
    }

    @DisplayName("report - partyId 가 null 이면 방을 조회하지 않고 실패")
    @Test
    void report_nullPartyId() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.report(REPORTER_ID, null, LATITUDE, LONGITUDE));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReportErrorCode.REPORT_NOT_ALLOWED);
        verifyNoInteractions(partyAccess);
        verifyNoInteractions(reports);
    }

    @DisplayName("report - 운행 중 승객이 아니면 스냅샷 조회와 저장 없이 실패")
    @Test
    void report_notRidingMember() {

        /*
         * settings
         */
        doReturn(false).when(partyAccess).isRidingMember(eq(PARTY_ID), eq(REPORTER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.report(REPORTER_ID, PARTY_ID, LATITUDE, LONGITUDE));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReportErrorCode.REPORT_NOT_ALLOWED);
        verify(partyAccess, never()).findSummary(any());
        verifyNoInteractions(reports);
    }

    @DisplayName("report - 신고자가 null 이면 저장하지 않고 실패")
    @Test
    void report_nullReporter() {

        /*
         * settings
         */
        doReturn(true).when(partyAccess).isRidingMember(eq(PARTY_ID), eq(null));
        doReturn(Optional.of(summary(DRIVER_ID))).when(partyAccess).findSummary(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.report(null, PARTY_ID, LATITUDE, LONGITUDE));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReportErrorCode.REPORTER_REQUIRED);
        verifyNoInteractions(reports);
    }

    @DisplayName("report - 저장소 예외는 그대로 전파")
    @Test
    void report_saveFailed() {

        /*
         * settings
         */
        final RuntimeException failure = new RuntimeException("저장 실패");
        doReturn(true).when(partyAccess).isRidingMember(eq(PARTY_ID), eq(REPORTER_ID));
        doReturn(Optional.of(summary(DRIVER_ID))).when(partyAccess).findSummary(eq(PARTY_ID));
        doThrow(failure).when(reports).save(any(Report.class));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.report(REPORTER_ID, PARTY_ID, LATITUDE, LONGITUDE));

        /*
         * verify
         */
        assertThat(thrown).isSameAs(failure);
    }

    @DisplayName("confirmCall - 통화했으면 CALL_CONFIRMED 로 저장")
    @Test
    void confirmCall_called() {

        /*
         * settings
         */
        final Report report = reportOf(ReportStatus.REPORTED);
        doReturn(Optional.of(report)).when(reports).findLatestByReporterId(eq(REPORTER_ID));

        /*
         * test
         */
        service.confirmCall(REPORTER_ID, true);

        /*
         * verify
         */
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CALL_CONFIRMED);
        verify(reports).save(eq(report));
    }

    @DisplayName("confirmCall - 통화하지 않았으면 CALL_NOT_MADE 로 저장")
    @Test
    void confirmCall_notCalled() {

        /*
         * settings
         */
        final Report report = reportOf(ReportStatus.REPORTED);
        doReturn(Optional.of(report)).when(reports).findLatestByReporterId(eq(REPORTER_ID));

        /*
         * test
         */
        service.confirmCall(REPORTER_ID, false);

        /*
         * verify
         */
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CALL_NOT_MADE);
        verify(reports).save(eq(report));
    }

    @DisplayName("confirmCall - 신고가 없으면 실패")
    @Test
    void confirmCall_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(reports).findLatestByReporterId(eq(REPORTER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.confirmCall(REPORTER_ID, true));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReportErrorCode.REPORT_NOT_FOUND);
        verify(reports, never()).save(any());
    }

    @DisplayName("confirmCall - 이미 통화함으로 확정됐으면 상태를 바꾸지 않고 실패")
    @Test
    void confirmCall_alreadyCalled() {

        /*
         * settings
         */
        final Report report = reportOf(ReportStatus.CALL_CONFIRMED);
        doReturn(Optional.of(report)).when(reports).findLatestByReporterId(eq(REPORTER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.confirmCall(REPORTER_ID, false));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReportErrorCode.REPORT_ALREADY_CONFIRMED);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CALL_CONFIRMED);
        verify(reports, never()).save(any());
    }

    @DisplayName("confirmCall - 이미 통화 안 함으로 확정됐으면 상태를 바꾸지 않고 실패")
    @Test
    void confirmCall_alreadyNotCalled() {

        /*
         * settings
         */
        final Report report = reportOf(ReportStatus.CALL_NOT_MADE);
        doReturn(Optional.of(report)).when(reports).findLatestByReporterId(eq(REPORTER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.confirmCall(REPORTER_ID, true));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReportErrorCode.REPORT_ALREADY_CONFIRMED);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CALL_NOT_MADE);
        verify(reports, never()).save(any());
    }
}
