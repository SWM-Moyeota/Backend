package team.codingforest.moyeota.report.application;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.report.domain.exception.ReportErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.api.MatchingTarget;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartyChatSummary;
import team.codingforest.moyeota.matching.api.PartySummary;
import team.codingforest.moyeota.report.domain.Report;
import team.codingforest.moyeota.report.domain.Reports;
import team.codingforest.moyeota.report.domain.enums.ReportStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class ReportApplicationServiceTest {

    private static final Long 신고자 = 5L;
    private static final Long 방번호 = 1L;
    private static final Long 기사 = 9L;

    private InMemoryReports reports;
    private FakePartyAccess partyAccess;
    private ReportApplicationService service;

    @BeforeEach
    void setUp() {
        reports = new InMemoryReports();
        partyAccess = new FakePartyAccess();
        service = new ReportApplicationService(reports, partyAccess);
    }

    private void 운행중() {
        partyAccess.ridingPartyId = 방번호;
    }

    @Test
    void 운행_중_신고하면_배정_기사가_스냅샷으로_저장된다() {
        운행중();
        partyAccess.driverId = 기사;

        Long reportId = service.report(신고자, 방번호, 37.4979, 127.0276);

        Report saved = reports.findById(reportId).orElseThrow();
        assertThat(saved.getDriverId()).isEqualTo(기사);   // 방 데이터는 변하므로 신고 순간 복사돼야 함
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.REPORTED);
        assertThat(saved.getReporterLatitude()).isEqualTo(37.4979);
    }

    @Test
    void 스냅샷에_기사가_없어도_저장된다() {
        // 조회 시점 경합 등으로 driverId가 비어 돌아오는 경우 - 부가정보 결손이 저장을 막으면 안 된다
        운행중();
        partyAccess.driverId = null;

        Long reportId = service.report(신고자, 방번호, null, null);

        assertThat(reports.findById(reportId).orElseThrow().getDriverId()).isNull();
    }

    @Test
    void 운행_중이면_스냅샷_조회가_실패해도_저장된다() {
        // 가드(정책 검증)와 저장 원칙의 공존 - 가드 통과 후의 조회 실패는 저장을 막지 않는다
        운행중();
        partyAccess.explodeOnFindSummary = true;

        Long reportId = service.report(신고자, 방번호, null, null);

        Report saved = reports.findById(reportId).orElseThrow();
        assertThat(saved.getDriverId()).isNull();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.REPORTED);
    }

    @Test
    void 운행_중이_아니면_신고할_수_없다() {
        // 차단 정책(A): 신고 버튼은 운행 화면에만 있으므로 그 외 호출은 비정상
        // 주의: 현재 isRiding()은 IN_RIDE만 - 픽업 대기(DRIVER_ASSIGNED) 중 신고도 차단됨 (알려진 사각지대)
        assertThatThrownBy(() -> service.report(신고자, 방번호, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReportErrorCode.REPORT_NOT_ALLOWED);
        assertThat(reports.count()).isZero();
    }

    @Test
    void 남의_방_번호로는_신고할_수_없다() {
        // 본인은 운행 중이어도 다른 partyId를 적으면 거부 - 무고한 기사에게 신고 기록이 달리는 오염 방지
        운행중();

        assertThatThrownBy(() -> service.report(신고자, 999L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReportErrorCode.REPORT_NOT_ALLOWED);
        assertThat(reports.count()).isZero();
    }

    @Test
    void 방_번호_없이는_신고할_수_없다() {
        // partyId null이 JPA 예외(500)가 아니라 도메인 예외로 떨어져야 한다
        운행중();

        assertThatThrownBy(() -> service.report(신고자, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReportErrorCode.REPORT_NOT_ALLOWED);
    }

    @Test
    void 복귀_후_통화_여부를_확정한다() {
        운행중();
        Long reportId = service.report(신고자, 방번호, null, null);

        service.confirmCall(reportId, true);

        assertThat(reports.findById(reportId).orElseThrow().getStatus()).isEqualTo(ReportStatus.CALL_CONFIRMED);
    }

    @Test
    void 없는_신고는_확정할_수_없다() {
        assertThatThrownBy(() -> service.confirmCall(999L, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReportErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    void 이미_확정된_신고를_다시_확정하면_예외가_나고_기존_값이_유지된다() {
        운행중();
        Long reportId = service.report(신고자, 방번호, null, null);
        service.confirmCall(reportId, true);

        assertThatThrownBy(() -> service.confirmCall(reportId, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReportErrorCode.REPORT_ALREADY_CONFIRMED);
        assertThat(reports.findById(reportId).orElseThrow().getStatus()).isEqualTo(ReportStatus.CALL_CONFIRMED);
    }

    /** 인메모리 신고 저장소 - save가 id를 부여하고 restore로 복사본을 보관 */
    static class InMemoryReports implements Reports {
        private final Map<Long, Report> store = new HashMap<>();
        private Long sequence = 0L;

        @Override
        public Report save(Report report) {
            Long id = report.getId() != null ? report.getId() : ++sequence;
            Report saved = Report.restore(id, report.getReporterId(), report.getPartyId(), report.getDriverId(),
                    report.getReporterLatitude(), report.getReporterLongitude(), report.getStatus());

            store.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Report> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        int count() {
            return store.size();
        }
    }

    /** 신고 가드(isRidingMember)와 스냅샷(findSummary)만 제어하는 가짜 */
    static class FakePartyAccess implements PartyAccess {
        Long ridingPartyId;   // 신고자가 운행(IN_RIDE) 중인 방 - null이면 운행 중 아님
        Long driverId;
        boolean explodeOnFindSummary;

        @Override
        public boolean isRidingMember(Long partyId, Long memberId) {
            return partyId.equals(ridingPartyId);
        }

        @Override
        public Optional<PartySummary> findSummary(Long partyId) {
            if(explodeOnFindSummary) throw new IllegalStateException("DB 연결 실패 재현");
            if(partyId == null) throw new IllegalArgumentException("partyId 없음");   // 실제 JPA도 null 조회에 예외

            return Optional.of(new PartySummary(partyId, 37.4979, 127.0276, 37.3948, 127.1112,
                    "강남역", "판교역", 2, 12000, 25, driverId));
        }

        @Override public void assignDriver(Long partyId, Long driverId) {}
        @Override public void failMatching(Long partyId) {}
        @Override public List<MatchingTarget> findMatchingTargets() { return List.of(); }
        @Override public void startRide(Long partyId, Long driverId) {}
        @Override public void completeRide(Long partyId, Long driverId, int fare) {}
        @Override public boolean isAwaitingPickup(Long partyId, Long driverId) { return false; }
        @Override public boolean hasOngoingRide(Long driverId) { return false; }
        @Override public boolean hasMemberOnParty(Long memberId, Long partyId) { return true; }
        @Override public PartyChatSummary findChatSummary(Long partyId) { return null; }
    }
}
