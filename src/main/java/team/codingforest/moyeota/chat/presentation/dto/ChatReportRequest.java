package team.codingforest.moyeota.chat.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import team.codingforest.moyeota.chat.domain.enums.ReportReasonType;

public record ChatReportRequest (
        @NotNull(message = "신고할 메시지 ID는 필수입니다")
        @Positive(message = "메시지 ID는 1 이상이어야 합니다")
        Long chatMessageId,

        @NotNull(message = "신고 사유는 필수입니다")
        ReportReasonType reason,

        @Size(max = 500, message = "상세 내용은 500자를 넘을 수 없습니다")
        String description
){
}
