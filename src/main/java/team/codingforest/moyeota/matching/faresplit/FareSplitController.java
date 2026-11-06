package team.codingforest.moyeota.matching.faresplit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "택시비 정산", description = "동승이 끝난 뒤 택시비를 1/N 로 나누는 도우미. 함께 탄 사람(출발지 도착을 누르고 노쇼가 아닌 멤버) 중 한 명이 택시비를 다 내고 실제 요금과 받을 계좌를 넣으면, 나머지 사람이 1인당 금액을 토스·은행 앱으로 각자 보낸다. 앱은 돈을 옮기지 않는다. 방마다 정산 하나")
@RestController
@RequestMapping("/api/v1/matching/rooms/{partyId}/fare-split")
@RequiredArgsConstructor
public class FareSplitController {
    private final FareSplitService fareSplitService;

    @Operation(summary = "정산 시작", description = "토큰의 사용자가 받는 사람(PAYER)이 된다. 동승이 끝난(FINISHED) 방의 탑승자만 시작할 수 있고, 받는 사람을 포함한 탑승자가 2명 이상이어야 한다. 받는 사람을 뺀 탑승자가 보내는 사람(SENDER)이 된다. 1인당 금액 = 택시비 / 탑승자 수를 10원 단위로 올린 값, 받는 사람 몫 = 택시비 - 1인당 금액 × 보내는 사람 수. 계좌번호는 이 정산에만 암호화해 저장하고 정산이 끝나면 지운다. 응답은 정산 상세(받는 사람 화면)")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "시작한 정산 상세"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - 필드 누락·없는 은행 코드 / INVALID_FARE - 택시비가 1,000~500,000원 밖 / INVALID_ACCOUNT_NUMBER - 계좌번호가 숫자 10~16자리가 아님"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER - 이 방의 멤버가 아님 / NOT_A_RIDER - 출발지 도착을 누르지 않았거나 노쇼인 멤버"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_NOT_FINISHED - 아직 출발하지 않은 방 / NOT_ENOUGH_RIDERS - 함께 탄 사람이 혼자 / FARE_SPLIT_ALREADY_EXISTS - 이미 정산을 시작한 방(GET 으로 본다)")})
    @PostMapping
    public ResponseEntity<FareSplitDetailResponse> create(@PathVariable Long partyId, @CurrentUser Long memberId, @Valid @RequestBody FareSplitRequest request) {
        FareSplitDetailResponse detail = fareSplitService.create(partyId, memberId, request.totalFare(), request.bankCode(), request.accountNumber());

        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
    }

    @Operation(summary = "정산 상세", description = "받는 사람과 보내는 사람만 볼 수 있다. myRole·myAmount 는 토큰의 사용자 기준. accountNumber 는 OPEN 일 때만, tossDeeplink 는 보내는 사람이 OPEN 정산을 볼 때만 있다. transfers 는 받는 사람을 뺀 탑승자별 송금(방 합류 순)이고, maskedRealName(가린 실명)은 받는 사람이 볼 때만 있다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "정산 상세"), @ApiResponse(responseCode = "403", description = "NOT_FARE_SPLIT_PARTICIPANT - 받는 사람도 보내는 사람도 아님"), @ApiResponse(responseCode = "404", description = "FARE_SPLIT_NOT_FOUND - 이 방에 정산이 없음")})
    @GetMapping
    public ResponseEntity<FareSplitDetailResponse> detail(@PathVariable Long partyId, @CurrentUser Long memberId) {
        return ResponseEntity.ok(fareSplitService.getDetail(partyId, memberId));
    }
}
