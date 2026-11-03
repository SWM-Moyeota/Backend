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
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.dto.SettlementDetailResponse;
import team.codingforest.moyeota.settlement.ledger.dto.WeeklySettlementResponse;

import java.time.Instant;
import java.time.LocalDate;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "정산", description = "기사 정산. 기사가 요금을 입력해 결제 그룹이 만들어지면, 승객 결제 결과와 상관없이 결제 그룹 총금액으로 정산 원장에 미정산 운행으로 기록되고, 주(KST 월요일 00:00 ~ 다음 월요일 00:00)가 끝나면 기사마다 정산서로 묶여 지급된다. 배치 일정(KST) - 정산서 생성: 월요일 00:05, 만들지 못한 기사는 같은 날 09:05 까지 매시 5분에 다시 시도(이미 정산서가 있는 기사는 건너뜀) / 지급: 매일 10:00, 지급 보류(HOLD)·실패(FAILED) 정산서도 이때 다시 시도(실패한 지급이 3건 쌓이면 더 시도하지 않음) / 결과를 모르는 지급(UNKNOWN): 10분마다 결제사에 확인")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettlementController {
    private final SettlementQueryService settlementQueryService;

    @Operation(summary = "주별 정산", description = "내가 한 주(KST 월요일 00:00 ~ 다음 월요일 00:00)에 받을 정산을 준다. 그 주 정산서가 있으면 정산서 금액을, 없으면 settlementId·status 를 null 로 두고 그 주에 끝난 미정산 운행의 예정 금액을 준다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "그 주 정산"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - date 가 yyyy-MM-dd 형식이 아니거나 없는 날짜(예: 2026-02-30)"), @ApiResponse(responseCode = "404", description = "DRIVER_NOT_REGISTERED - 토큰의 유저가 기사로 등록되어 있지 않음")})
    @GetMapping("/drivers/me/settlements")
    public ResponseEntity<WeeklySettlementResponse> findWeekly(@CurrentDriver Long driverId,
                                                               @Parameter(description = "조회할 주에 속한 아무 날짜(KST), yyyy-MM-dd. 생략하거나 빈 값이면 오늘(KST)이 속한 주", example = "2026-10-28", schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        SettlementPeriod week = date != null ? SettlementPeriod.weekOf(date) : SettlementPeriod.weekOf(Instant.now());
        return ResponseEntity.ok(settlementQueryService.findWeekly(driverId, week));
    }

    @Operation(summary = "정산서 상세", description = "내 정산서 하나와 그 안에 담긴 운행별 정산 원장, 가장 최근 지급 시도를 준다. 다른 기사의 정산서는 없는 정산서와 똑같이 404 다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "정산서 상세"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - settlementId 가 정수가 아님"), @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND - 없는 정산서이거나 다른 기사의 정산서 / DRIVER_NOT_REGISTERED - 토큰의 유저가 기사로 등록되어 있지 않음")})
    @GetMapping("/drivers/me/settlements/{settlementId}")
    public ResponseEntity<SettlementDetailResponse> findDetail(@CurrentDriver Long driverId,
                                                               @Parameter(description = "정산서 ID - 주별 정산의 settlementId") @PathVariable Long settlementId) {
        return ResponseEntity.ok(settlementQueryService.findDetail(driverId, settlementId));
    }
}
