package team.codingforest.moyeota.matching.faresplit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.matching.faresplit.domain.TransferStatus;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitRequest;
import team.codingforest.moyeota.matching.faresplit.dto.MyFareSplitListResponse;
import team.codingforest.moyeota.user.api.CurrentUser;

import java.util.UUID;

@Tag(name = "택시비 정산", description = "동승이 끝난 뒤 택시비를 1/N 로 나누는 도우미. 함께 탄 사람(출발지 도착을 누르고 노쇼가 아닌 멤버) 중 한 명이 택시비를 다 내고 실제 요금과 받을 계좌를 넣으면, 나머지 사람이 1인당 금액을 토스·은행 앱으로 각자 보낸다. 앱은 돈을 옮기지 않는다. 방마다 정산 하나. 송금 상태는 NOT_SENT(아직 안 보냄) → SENT(보내는 사람이 보냈다고 누름) → CONFIRMED(받는 사람이 받았다고 확인)이고, 모든 송금이 CONFIRMED 가 되면 정산이 COMPLETED 로 닫히며 계좌번호를 지운다")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FareSplitController {
    private static final String FARE_SPLIT = "/matching/rooms/{partyId}/fare-split";

    private final FareSplitService fareSplitService;

    @Operation(summary = "정산 시작", description = "토큰의 사용자가 받는 사람(PAYER)이 된다. 동승이 끝난(FINISHED) 방의 탑승자만 시작할 수 있고, 받는 사람을 포함한 탑승자가 2명 이상이어야 한다. 받는 사람을 뺀 탑승자가 보내는 사람(SENDER)이 된다. 1인당 금액 = 택시비 / 탑승자 수를 10원 단위로 올린 값, 받는 사람 몫 = 택시비 - 1인당 금액 × 보내는 사람 수. 계좌번호는 이 정산에만 암호화해 저장하고 정산이 끝나면 지운다. 응답은 정산 상세(받는 사람 화면)")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "시작한 정산 상세"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - 필드 누락·없는 은행 코드 / INVALID_FARE - 택시비가 1,000~500,000원 밖 / INVALID_ACCOUNT_NUMBER - 계좌번호가 숫자 10~16자리가 아님"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER - 이 방의 멤버가 아님 / NOT_A_RIDER - 출발지 도착을 누르지 않았거나 노쇼인 멤버"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_NOT_FINISHED - 아직 출발하지 않은 방 / NOT_ENOUGH_RIDERS - 함께 탄 사람이 혼자 / FARE_SPLIT_ALREADY_EXISTS - 이미 정산을 시작한 방(GET 으로 본다)")})
    @PostMapping(FARE_SPLIT)
    public ResponseEntity<FareSplitDetailResponse> create(@PathVariable Long partyId, @CurrentUser Long memberId, @Valid @RequestBody FareSplitRequest request) {
        FareSplitDetailResponse detail = fareSplitService.create(partyId, memberId, request.totalFare(), request.bankCode(), request.accountNumber());

        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
    }

    @Operation(summary = "정산 상세", description = "받는 사람과 보내는 사람만 볼 수 있다. myRole·myAmount 는 토큰의 사용자 기준. accountNumber 는 OPEN 일 때만, tossDeeplink 는 보내는 사람이 OPEN 정산을 볼 때만 있다. transfers 는 받는 사람을 뺀 탑승자별 송금(방 합류 순)이고, maskedRealName(가린 실명)은 받는 사람이 볼 때만 있다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "정산 상세"), @ApiResponse(responseCode = "403", description = "NOT_FARE_SPLIT_PARTICIPANT - 받는 사람도 보내는 사람도 아님"), @ApiResponse(responseCode = "404", description = "FARE_SPLIT_NOT_FOUND - 이 방에 정산이 없음(취소된 정산 포함)")})
    @GetMapping(FARE_SPLIT)
    public ResponseEntity<FareSplitDetailResponse> detail(@PathVariable Long partyId, @CurrentUser Long memberId) {
        return ResponseEntity.ok(fareSplitService.getDetail(partyId, memberId));
    }

    @Operation(summary = "정산 수정", description = "받는 사람이 택시비·은행·계좌번호를 고친다(세 필드 모두 필수, 규칙은 정산 시작과 같다). 아무도 보냈다고 누르거나 확인되기 전(모든 송금이 NOT_SENT)인 OPEN 정산만 고칠 수 있다. 1인당 금액·받는 사람 몫을 다시 계산한다. 응답은 고친 정산 상세(받는 사람 화면)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "고친 정산 상세"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - 필드 누락·없는 은행 코드 / INVALID_FARE / INVALID_ACCOUNT_NUMBER"), @ApiResponse(responseCode = "403", description = "NOT_FARE_SPLIT_PARTICIPANT - 정산에 없는 사람 / NOT_FARE_SPLIT_PAYER - 보내는 사람"), @ApiResponse(responseCode = "404", description = "FARE_SPLIT_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "FARE_SPLIT_CLOSED - 닫힌(COMPLETED·EXPIRED) 정산 / FARE_SPLIT_ALREADY_STARTED - 누군가 보냈다고 눌렀거나 확인된 송금이 있음")})
    @PatchMapping(FARE_SPLIT)
    public ResponseEntity<FareSplitDetailResponse> edit(@PathVariable Long partyId, @CurrentUser Long memberId, @Valid @RequestBody FareSplitRequest request) {
        return ResponseEntity.ok(fareSplitService.edit(partyId, memberId, request.totalFare(), request.bankCode(), request.accountNumber()));
    }

    @Operation(summary = "정산 취소", description = "받는 사람이 정산을 지운다. 수정과 같은 조건(아무도 보내기 전인 OPEN 정산)이다. 정산과 송금 기록·계좌번호가 모두 지워져 이후 상세는 404 이고, 같은 방에서 다시 시작할 수 있다. 응답 본문 없음")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "취소됨"), @ApiResponse(responseCode = "403", description = "NOT_FARE_SPLIT_PARTICIPANT / NOT_FARE_SPLIT_PAYER"), @ApiResponse(responseCode = "404", description = "FARE_SPLIT_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "FARE_SPLIT_CLOSED / FARE_SPLIT_ALREADY_STARTED")})
    @PostMapping(FARE_SPLIT + "/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long partyId, @CurrentUser Long memberId) {
        fareSplitService.cancel(partyId, memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "보냈어요", description = "보내는 사람이 토스·은행 앱으로 1인당 금액을 보낸 뒤 누른다(NOT_SENT → SENT, sentAt 기록). 이미 SENT·CONFIRMED 면 아무것도 바꾸지 않고 204 라 다시 눌러도 된다. 응답 본문 없음")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "SENT 가 됐거나 이미 SENT·CONFIRMED"), @ApiResponse(responseCode = "403", description = "NOT_FARE_SPLIT_PARTICIPANT - 정산에 없는 사람 / NOT_FARE_SPLIT_SENDER - 받는 사람"), @ApiResponse(responseCode = "404", description = "FARE_SPLIT_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "FARE_SPLIT_CLOSED - 보내기 전에 정산이 닫힘(EXPIRED)")})
    @PostMapping(FARE_SPLIT + "/sent")
    public ResponseEntity<Void> markSent(@PathVariable Long partyId, @CurrentUser Long memberId) {
        fareSplitService.markSent(partyId, memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "받았어요", description = "받는 사람이 publicId(보내는 사람)의 송금을 받았다고 확인한다(→ CONFIRMED, confirmedAt 기록). 보냈다고 누르기 전(NOT_SENT)이어도 확인할 수 있다(현금 등). 이미 CONFIRMED 면 아무것도 바꾸지 않고 204. 모든 송금이 CONFIRMED 가 되면 정산이 COMPLETED 로 닫히고(closedAt) 계좌번호를 지운다. 응답 본문 없음")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "CONFIRMED 가 됐거나 이미 CONFIRMED"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - publicId 가 UUID 가 아님"), @ApiResponse(responseCode = "403", description = "NOT_FARE_SPLIT_PARTICIPANT / NOT_FARE_SPLIT_PAYER - 보내는 사람"), @ApiResponse(responseCode = "404", description = "FARE_SPLIT_NOT_FOUND / FARE_SPLIT_TRANSFER_NOT_FOUND - publicId 가 이 정산의 보내는 사람이 아님"), @ApiResponse(responseCode = "409", description = "FARE_SPLIT_CLOSED - 확인 전에 정산이 닫힘(EXPIRED)")})
    @PostMapping(FARE_SPLIT + "/transfers/{publicId}/received")
    public ResponseEntity<Void> confirmReceived(@PathVariable Long partyId, @Parameter(description = "보내는 사람의 publicId(정산 상세 transfers[].publicId)") @PathVariable UUID publicId, @CurrentUser Long memberId) {
        fareSplitService.confirmReceived(partyId, memberId, publicId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "못 받았어요", description = "받는 사람이 publicId(보내는 사람)의 송금이 아직 들어오지 않았다고 되돌린다(SENT → NOT_SENT, sentAt 을 지운다). SENT 인 송금에서만 할 수 있다. 응답 본문 없음")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "NOT_SENT 로 되돌림"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - publicId 가 UUID 가 아님"), @ApiResponse(responseCode = "403", description = "NOT_FARE_SPLIT_PARTICIPANT / NOT_FARE_SPLIT_PAYER - 보내는 사람"), @ApiResponse(responseCode = "404", description = "FARE_SPLIT_NOT_FOUND / FARE_SPLIT_TRANSFER_NOT_FOUND - publicId 가 이 정산의 보내는 사람이 아님"), @ApiResponse(responseCode = "409", description = "FARE_SPLIT_CLOSED - 닫힌 정산 / TRANSFER_NOT_SENT - 그 송금이 SENT 가 아님(아직 안 보냈거나 이미 확인함)")})
    @PostMapping(FARE_SPLIT + "/transfers/{publicId}/not-received")
    public ResponseEntity<Void> markNotReceived(@PathVariable Long partyId, @Parameter(description = "보내는 사람의 publicId(정산 상세 transfers[].publicId)") @PathVariable UUID publicId, @CurrentUser Long memberId) {
        fareSplitService.markNotReceived(partyId, memberId, publicId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내가 보낼 정산", description = "내가 보내는 사람인 진행 중(OPEN) 정산 중 내 송금이 status 인 것을 정산 시작 최신순으로 준다. 기본 NOT_SENT - 홈 배너(아직 보내지 않은 택시비)에 쓴다. 받는 사람으로 시작한 정산과 닫힌(COMPLETED·EXPIRED) 정산은 나오지 않는다. 항목을 누르면 partyId 로 정산 상세를 연다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "정산 목록(없으면 빈 배열)"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - status 가 NOT_SENT·SENT·CONFIRMED 가 아님")})
    @GetMapping("/users/me/fare-splits")
    public ResponseEntity<MyFareSplitListResponse> findMine(@CurrentUser Long memberId,
                                                            @Parameter(description = "내 송금 상태 NOT_SENT(기본) / SENT / CONFIRMED") @RequestParam(defaultValue = "NOT_SENT") TransferStatus status) {
        return ResponseEntity.ok(fareSplitService.findMine(memberId, status));
    }
}
