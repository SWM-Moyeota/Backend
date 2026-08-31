package team.codingforest.moyeota.report.application;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.report.domain.exception.ReportErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.report.domain.Report;
import team.codingforest.moyeota.report.domain.Reports;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportApplicationService {
    private final Reports reports;
    private final PartyAccess partyAccess;

    /**
     *  긴급 신고 사전 기록 - 다이얼 전환 직전 호출.
     *  스냅샷 조회가 실패해도 신고 저장 자체는 절대 실패하지 않는다.
     */
    @Transactional
    public Long report(Long reporterId, Long partyId, Double latitude, Double longitude) {
        if(partyId == null || !partyAccess.isRidingMember(partyId, reporterId)) throw new BusinessException(ReportErrorCode.REPORT_NOT_ALLOWED);

        Long driverId = null;

        try {
            driverId = partyAccess.findSummary(partyId)
                    .map(summary -> summary.driverId())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("신고 스냅샷 조회 실패 - 스냅샷 없이 저장 진행 partyId={}", partyId, e);
        }

        Report saved = reports.save(Report.create(reporterId, partyId, driverId, latitude, longitude));

        log.warn("긴급 신고 접수 reportId={}, reporterId={}, partyId={}, driverId={}", saved.getId(), reporterId, partyId, driverId);

        return saved.getId();
    }

    /**
     *  복귀 후 통화 여부 보강
     */
    @Transactional
    public void confirmCall(Long reportId, boolean called) {
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));

        report.confirmCall(called);
        reports.save(report);

        log.info("신고 통화 여부 확정 reportId={}, called={}", reportId, called);
    }
}
