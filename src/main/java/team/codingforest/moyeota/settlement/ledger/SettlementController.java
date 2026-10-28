package team.codingforest.moyeota.settlement.ledger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.driver.api.CurrentDriver;
import team.codingforest.moyeota.settlement.ledger.dto.SettlementDetailResponse;
import team.codingforest.moyeota.settlement.ledger.dto.WeeklySettlementResponse;

import java.time.Instant;
import java.time.LocalDate;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "정산", description = "기사 정산. 주(KST 월요일 00:00 ~ 다음 월요일 00:00) 단위 정산서와 운행별 정산 원장, 지급 결과. 기사가 요금을 입력해 운행을 마치면 결제 결과와 상관없이 바로 정산 원장에 미정산 운행으로 기록된다. 주가 끝난 뒤 월요일 00:05(KST) 배치가 그 주까지 끝난 미정산 운행을 기사마다 정산서로 묶고, 이때 생성에 실패한 기사는 같은 날 09:05 까지 매시 5분 배치가 다시 만든다. 지급은 매일 10:00(KST) 에 하므로 정상 지급은 월요일 10:00 이고, 지급 보류·실패는 다음 10:00 에 다시 시도한다")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettlementController {
    private final SettlementQueryService settlementQueryService;

    @Operation(summary = "주별 정산", description = "토큰의 기사가 받을 정산을 한 주(KST 월요일 00:00 ~ 다음 월요일 00:00) 단위로 준다. date 가 속한 월요일~일요일 주를 주고, date 를 생략하면 오늘(KST)이 속한 주다. 운행은 기사가 요금을 입력해 마치는 순간 결제 결과와 상관없이 바로 정산 원장에 기록되어 여기에 잡힌다. 응답은 셋 중 하나다. 정산서가 있는 지난 주 - settlementId·status 가 있고 amount·days 는 정산서에 담긴 운행으로 센다. 이번 주 - settlementId·status 는 null 이고 amount·days 는 이번 주에 끝났고 아직 정산서에 담기지 않은 운행으로 센 예정 금액이다. 정산서가 없는 지난 주와 미래 주 - settlementId·status 는 null, amount 는 0, days 7칸이 모두 0 이다. 그래서 월요일 00:05 배치가 지난 주 정산서를 만들기 전까지 지난 주 운행은 지난 주와 이번 주 어디에도 잡히지 않는다. 각 필드의 뜻은 응답 스키마에 있다. 다른 기사의 정산서는 나오지 않는다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "그 주 정산"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - date 가 yyyy-MM-dd 형식이 아니거나 없는 날짜(예: 2026-02-30)"), @ApiResponse(responseCode = "404", description = "DRIVER_NOT_REGISTERED - 토큰의 유저가 기사로 등록되어 있지 않음")})
    @GetMapping("/drivers/me/settlements")
    public ResponseEntity<WeeklySettlementResponse> findWeekly(@CurrentDriver Long driverId,
                                                               @Parameter(description = "조회할 주에 속한 아무 날짜(KST), yyyy-MM-dd. 그 날짜가 속한 월요일~일요일 주를 준다. 생략하거나 빈 값이면 오늘(KST)이 속한 주", example = "2026-10-28", schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(settlementQueryService.findWeekly(driverId, date, Instant.now()));
    }

    @Operation(summary = "정산서 상세", description = "내 정산서(한 주) 하나와 그 안에 담긴 운행별 정산 원장, 가장 최근 지급 시도를 준다. date 는 그 주의 월요일(KST)이고 정산 기간은 periodStart ~ periodEnd(다음 월요일 00:00, 포함하지 않음)다. 운행은 기사가 요금을 입력해 마치는 순간 결제 결과와 상관없이 바로 정산 원장에 기록되고, 정산서는 이 주가 끝난 뒤 월요일 00:05(KST) 배치(생성에 실패한 기사는 같은 날 09:05 까지 매시 5분 배치)가 periodEnd 전에 끝났고 아직 정산서에 담기지 않은 운행을 모아 만든다. 만든 뒤에는 금액과 운행 목록이 바뀌지 않는다. rides 는 운행 종료 시각(occurredAt) 오름차순이고, 각 운행의 settlementAmount = totalFare + platformPromotion(수수료 없음), 정산서 amount 는 rides 의 settlementAmount 합이다. payout 은 가장 최근 지급 시도 1건이고 지급을 시도한 적이 없으면 null 이다. 지급 계좌번호는 끝 4자리만 보이게 가려서 주고, 예금주 이름과 전체 계좌번호, 결제사 거래 ID 는 내려주지 않는다. 다른 기사의 정산서는 없는 정산서와 똑같이 404 다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "정산서 상세"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - settlementId 가 정수가 아님"), @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND - 없는 정산서이거나 다른 기사의 정산서 / DRIVER_NOT_REGISTERED - 토큰의 유저가 기사로 등록되어 있지 않음")})
    @GetMapping("/drivers/me/settlements/{settlementId}")
    public ResponseEntity<SettlementDetailResponse> findDetail(@CurrentDriver Long driverId,
                                                               @Parameter(description = "정산서 ID - 주별 정산의 settlementId") @PathVariable Long settlementId) {
        return ResponseEntity.ok(settlementQueryService.findDetail(driverId, settlementId));
    }
}
