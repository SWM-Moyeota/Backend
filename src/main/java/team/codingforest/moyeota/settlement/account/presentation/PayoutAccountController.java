package team.codingforest.moyeota.settlement.account.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.driver.api.CurrentDriver;
import team.codingforest.moyeota.settlement.account.application.PayoutAccountService;
import team.codingforest.moyeota.settlement.account.dto.PayoutAccountChangeRequest;
import team.codingforest.moyeota.settlement.account.dto.PayoutAccountRegisterRequest;
import team.codingforest.moyeota.settlement.account.dto.PayoutAccountResponse;

@ConditionalOnProperty(prefix = "moyeota.settlement.toss", name = "enabled", havingValue = "true")
@Tag(name = "지급 계좌", description = "기사가 정산금을 받을 계좌. 결제사(토스페이먼츠) 지급대행 셀러로 등록되고, 셀러 상태가 PARTIALLY_APPROVED 또는 APPROVED 일 때만 지급된다. 계좌번호 원문은 저장하지 않고 끝 4자리만 보여준다")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PayoutAccountController {
    private final PayoutAccountService payoutAccountService;

    @Operation(summary = "지급 계좌 등록", description = "사업자 정보와 계좌로 결제사 셀러를 등록한다")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "등록된 지급 계좌"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - 입력 형식 오류 / PAYOUT_ACCOUNT_REJECTED - 결제사가 사업자 정보나 계좌를 거절함"),
            @ApiResponse(responseCode = "404", description = "DRIVER_NOT_REGISTERED - 토큰의 유저가 기사로 등록되어 있지 않음"),
            @ApiResponse(responseCode = "409", description = "PAYOUT_ACCOUNT_ALREADY_REGISTERED - 이미 등록된 지급 계좌가 있음 / PAYOUT_ACCOUNT_REGISTERING - 같은 기사의 등록이 처리 중"),
            @ApiResponse(responseCode = "502", description = "PAYOUT_PROVIDER_UNAVAILABLE - 결제사 응답 없음, 잠시 후 다시 등록")})
    @PostMapping("/drivers/me/payout-account")
    public ResponseEntity<PayoutAccountResponse> register(@CurrentDriver Long driverId, @Valid @RequestBody PayoutAccountRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payoutAccountService.register(driverId, request.toCommand()));
    }

    @Operation(summary = "지급 계좌 변경", description = "등록된 셀러의 은행·계좌번호·예금주를 바꾼다. 사업자 정보는 바꿀 수 없다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "바뀐 지급 계좌"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - 입력 형식 오류 / PAYOUT_ACCOUNT_REJECTED - 결제사가 계좌를 거절함"),
            @ApiResponse(responseCode = "404", description = "PAYOUT_ACCOUNT_NOT_FOUND - 등록이 끝난 지급 계좌가 없음 / DRIVER_NOT_REGISTERED - 토큰의 유저가 기사로 등록되어 있지 않음"),
            @ApiResponse(responseCode = "409", description = "PAYOUT_ACCOUNT_LOCKED - 결제사가 지금은 변경을 막음"),
            @ApiResponse(responseCode = "502", description = "PAYOUT_PROVIDER_UNAVAILABLE - 결제사 응답 없음")})
    @PutMapping("/drivers/me/payout-account")
    public ResponseEntity<PayoutAccountResponse> changeAccount(@CurrentDriver Long driverId, @Valid @RequestBody PayoutAccountChangeRequest request) {
        return ResponseEntity.ok(payoutAccountService.changeAccount(driverId, request.bankCode(), request.accountNumber(), request.accountHolder()));
    }

    @Operation(summary = "지급 계좌 조회", description = "내 지급 계좌와 셀러 상태를 준다. 승인 대기 중이면 결제사에서 상태를 다시 읽는다. sellerStatus 가 null 이면 등록이 끝나지 않은 것이라 잠시 후 다시 등록해야 한다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "지급 계좌"),
            @ApiResponse(responseCode = "404", description = "PAYOUT_ACCOUNT_NOT_FOUND - 지급 계좌가 없음 / DRIVER_NOT_REGISTERED - 토큰의 유저가 기사로 등록되어 있지 않음")})
    @GetMapping("/drivers/me/payout-account")
    public ResponseEntity<PayoutAccountResponse> find(@CurrentDriver Long driverId) {
        return ResponseEntity.ok(payoutAccountService.find(driverId));
    }
}
