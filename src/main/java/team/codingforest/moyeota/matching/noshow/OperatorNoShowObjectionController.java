package team.codingforest.moyeota.matching.noshow;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionStatus;
import team.codingforest.moyeota.matching.noshow.dto.ObjectionListResponse;

/**
 *  운영자 전용. 인증은 SecurityConfig 의 운영자 체인(X-Operator-Token)이 하고 사용자 JWT 는 받지 않는다
 */
@Tag(name = "운영자 - 노쇼 이의", description = "노쇼 이의 신청 조회·처리. 사용자 토큰(Bearer)이 아니라 X-Operator-Token 헤더(서버 설정 OPERATOR_TOKEN)로 호출한다. 헤더가 없거나 틀리거나 서버에 토큰이 설정되지 않았으면 401 USER006")
@SecurityRequirements   // 전역 Bearer 요구사항 해제 - 헤더는 각 오퍼레이션의 파라미터로 적는다
@RestController
@RequestMapping("/api/v1/operator/no-show-objections")
@RequiredArgsConstructor
public class OperatorNoShowObjectionController {
    private static final String OPERATOR_UNAUTHORIZED = "USER006 - X-Operator-Token 누락·불일치, 또는 서버에 운영자 토큰이 설정되지 않음";

    private final NoShowObjectionService noShowObjectionService;

    @Operation(summary = "이의 신청 목록", description = "상태별(기본 PENDING) 신청 시각 오래된 순, 최대 100개. 판단에 쓰도록 방의 출발지·도착지·약속 시간을 함께 준다",
            parameters = @Parameter(in = ParameterIn.HEADER, name = "X-Operator-Token", required = true, schema = @Schema(type = "string")))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "이의 신청 목록(없으면 빈 배열)"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - status 가 PENDING/ACCEPTED/REJECTED 가 아님"), @ApiResponse(responseCode = "401", description = OPERATOR_UNAUTHORIZED)})
    @GetMapping
    public ResponseEntity<ObjectionListResponse> list(@Parameter(description = "PENDING(기본) / ACCEPTED / REJECTED") @RequestParam(defaultValue = "PENDING") ObjectionStatus status) {
        return ResponseEntity.ok(ObjectionListResponse.from(noShowObjectionService.findObjections(status)));
    }

    @Operation(summary = "이의 인정", description = "PENDING 인 신청만. 그 방의 노쇼를 무효로 돌려(user_match_room.no_show_voided_at, user_no_show.voided_at) 사용자의 노쇼 횟수에서 빼고 지금 걸린 이용 제한을 푼다. 한 트랜잭션",
            parameters = @Parameter(in = ParameterIn.HEADER, name = "X-Operator-Token", required = true, schema = @Schema(type = "string")))
    @ApiResponses({@ApiResponse(responseCode = "204", description = "인정됨"), @ApiResponse(responseCode = "401", description = OPERATOR_UNAUTHORIZED), @ApiResponse(responseCode = "404", description = "OBJECTION_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "OBJECTION_ALREADY_RESOLVED - 이미 인정·거절됨")})
    @PostMapping("/{objectionId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long objectionId) {
        noShowObjectionService.accept(objectionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "이의 거절", description = "PENDING 인 신청만. 노쇼와 제재는 그대로 둔다",
            parameters = @Parameter(in = ParameterIn.HEADER, name = "X-Operator-Token", required = true, schema = @Schema(type = "string")))
    @ApiResponses({@ApiResponse(responseCode = "204", description = "거절됨"), @ApiResponse(responseCode = "401", description = OPERATOR_UNAUTHORIZED), @ApiResponse(responseCode = "404", description = "OBJECTION_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "OBJECTION_ALREADY_RESOLVED - 이미 인정·거절됨")})
    @PostMapping("/{objectionId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long objectionId) {
        noShowObjectionService.reject(objectionId);
        return ResponseEntity.noContent().build();
    }
}
