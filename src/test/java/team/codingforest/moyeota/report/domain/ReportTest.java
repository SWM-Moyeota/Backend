package team.codingforest.moyeota.report.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.report.domain.enums.ReportStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class ReportTest {
    private static final Long 신고자 = 5L;
    private static final Long 방번호 = 1L;
    private static final Long 기사 = 9L;
    private static final Instant 지금 = Instant.now();

    @Test
    void 신고를_생성하면_미확정_상태로_시작한다() {
        Report report = Report.create(신고자, 방번호, 기사, 37.4979, 127.0276, 지금);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.REPORTED);
        assertThat(report.getDriverId()).isEqualTo(기사);
        assertThat(report.getReportedAt()).isEqualTo(지금);
    }

    @Test
    void 신고자_없이는_생성할_수_없다() {
        // 유일한 필수값 - 나머지는 위급상황에서 못 실을 수 있다
        assertThatThrownBy(() -> Report.create(null, 방번호, 기사, 37.4979, 127.0276, 지금))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 신고자만_있으면_나머지가_전부_없어도_생성된다() {
        // "저장은 실패하지 않는다" 원칙 - 방 정보도 위치도 못 실은 최악의 위급상황
        Report report = Report.create(신고자, null, null, null, null, 지금);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.REPORTED);
        assertThat(report.getPartyId()).isNull();
        assertThat(report.getDriverId()).isNull();
    }

    @Test
    void 통화했다고_확정하면_CALL_CONFIRMED가_된다() {
        Report report = Report.create(신고자, 방번호, 기사, null, null, 지금);

        report.confirmCall(true);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.CALL_CONFIRMED);
    }

    @Test
    void 통화_안_했다고_확정하면_CALL_NOT_MADE가_된다() {
        Report report = Report.create(신고자, 방번호, 기사, null, null, 지금);

        report.confirmCall(false);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.CALL_NOT_MADE);
    }

    @Test
    void 이미_확정된_신고는_다시_확정할_수_없다() {
        // 확인 다이얼로그 이중 탭 - 첫 답변이 진실이다 (나중 값으로 덮이면 기록 조작 여지)
        Report report = Report.create(신고자, 방번호, 기사, null, null, 지금);
        report.confirmCall(true);

        assertThatThrownBy(() -> report.confirmCall(false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CALL_CONFIRMED);
    }

    @Test
    void 통화_안_함으로_확정한_뒤에도_번복할_수_없다() {
        Report report = Report.create(신고자, 방번호, 기사, null, null, 지금);
        report.confirmCall(false);

        assertThatThrownBy(() -> report.confirmCall(true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
