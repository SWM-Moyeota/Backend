package team.codingforest.moyeota.matching.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.matching.application.PartyLocationService;
import team.codingforest.moyeota.matching.application.dto.MemberLocationRequest;
import team.codingforest.moyeota.matching.application.dto.MemberLocationResult;
import team.codingforest.moyeota.user.api.CurrentUser;

import java.util.List;

@Tag(name = "동승자 위치", description = "탑승 지점에서 서로를 찾기 위한 실시간 위치 공유. Redis TTL 60초")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PartyLocationController {

    private final PartyLocationService service;

    @Operation(summary = "내 위치 보고", description = "방 화면이 열려 있는 동안 주기적으로 호출(권장 5초). 60초 안에 보고가 없으면 동승자 목록에서 사라진다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "갱신 완료"), @ApiResponse(responseCode = "400", description = "좌표 누락"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND")})
    @PostMapping("/matching/rooms/{partyId}/location")
    public ResponseEntity<Void> report(@PathVariable Long partyId, @CurrentUser Long memberId,
                                       @Valid @RequestBody MemberLocationRequest request) {
        service.report(partyId, memberId, request.latitude(), request.longitude());

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "동승자 위치 조회", description = "나를 뺀 멤버들의 최근 위치. 보고가 없거나 60초가 지난 멤버는 빠진다(빈 배열 가능)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "동승자 위치 목록"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND")})
    @GetMapping("/matching/rooms/{partyId}/locations")
    public ResponseEntity<List<MemberLocationResult>> findOthers(@PathVariable Long partyId, @CurrentUser Long memberId) {
        return ResponseEntity.ok(service.findOthers(partyId, memberId));
    }
}
