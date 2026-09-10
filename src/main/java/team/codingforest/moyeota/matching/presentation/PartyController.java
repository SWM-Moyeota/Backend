package team.codingforest.moyeota.matching.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.driver.api.DriverSummary;
import team.codingforest.moyeota.matching.application.PartyApplicationService;
import team.codingforest.moyeota.matching.application.dto.OpenPartyRequest;
import team.codingforest.moyeota.matching.application.dto.OpenPartyResponse;
import team.codingforest.moyeota.matching.application.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.application.dto.PartyListResponse;
import team.codingforest.moyeota.matching.application.dto.PartyResult;
import team.codingforest.moyeota.matching.application.dto.RouteRequest;
import team.codingforest.moyeota.matching.domain.RouteEstimate;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "매칭방", description = "동승 매칭방 생성·참여·조회. 정원이 차면 자동으로 매칭(기사 콜) 시작")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PartyController {
    private final PartyApplicationService service;

    @Operation(summary = "방 생성", description = "경로·예상 요금을 네이버로 계산해 저장. 생성자는 자동 참여. 진행 중인 방이 이미 있으면 409. body 의 creatorId 는 무시(토큰 사용)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "생성된 방"), @ApiResponse(responseCode = "400", description = "SAME_DEPARTURE_DESTINATION / INVALID_CAPACITY / INVALID_RADIUS / OUT_OF_SERVICE_AREA"), @ApiResponse(responseCode = "409", description = "ALREADY_JOINED_OTHER_PARTY"), @ApiResponse(responseCode = "502", description = "ROUTE_SEARCH_FAILED - 네이버 경로 API 장애")})
    @PostMapping("/matching/rooms")
    public ResponseEntity<OpenPartyResponse> open(@CurrentUser Long memberId, @RequestBody OpenPartyRequest request) {
        PartyResult party = service.open(request.toCommand(memberId));

        return ResponseEntity.ok(OpenPartyResponse.from(party));
    }

    @Operation(summary = "방 참여", description = "정원이 차면 COMPLETED → MATCHING 으로 넘어가며 기사 콜이 시작되고 채팅방이 열린다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "참여 후 방 상세(동승자 목록 포함)"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_FULL / PARTY_CLOSED / ALREADY_JOINED_PARTY / ALREADY_JOINED_OTHER_PARTY")})
    @PostMapping("/matching/rooms/{partyId}/join")
    public ResponseEntity<PartyDetailResult> join(@PathVariable Long partyId, @CurrentUser Long memberId) {
        return ResponseEntity.ok(service.join(partyId, memberId));
    }

    @Operation(summary = "방 나가기", description = "모집 중(ACTIVE/COMPLETED)에만 가능. 마지막 멤버가 나가면 방은 CANCELED")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "나감"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_NOT_RECRUITING - 매칭 시작 후")})
    @DeleteMapping("/matching/leave/{partyId}")
    public ResponseEntity<Void> leave(@PathVariable Long partyId, @CurrentUser Long memberId) {
        service.leave(partyId, memberId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "방 상세", description = "대기 화면 폴링용. members 에 동승자 publicId·닉네임·이미지·탑승 횟수. 본인 판별은 publicId 와 토큰 sub 비교")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 상세"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND")})
    @GetMapping("/matching/rooms/{partyId}")
    public ResponseEntity<PartyDetailResult> detail(@PathVariable Long partyId) {
        return ResponseEntity.ok(service.getPartyDetail(partyId));
    }

    @Operation(summary = "모집 중인 방 전체 목록", description = "파라미터 없이 호출하면 ACTIVE 전체. 지도 화면은 아래 영역 조회를 사용")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 목록")})
    @GetMapping("/matching/rooms")
    public ResponseEntity<PartyListResponse> list() {
        return ResponseEntity.ok(PartyListResponse.from(service.findActiveParties()));
    }

    @Operation(summary = "경로·요금 미리보기", description = "방 생성 전 예상 요금/시간/경로(polyline). 네이버 Directions 사용")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "예상 요금(원)·시간(분)·polyline"), @ApiResponse(responseCode = "400", description = "OUT_OF_SERVICE_AREA / INVALID_ROUTE_ESTIMATE"), @ApiResponse(responseCode = "404", description = "ROUTE_NOT_FOUND"), @ApiResponse(responseCode = "502", description = "ROUTE_SEARCH_FAILED")})
    @PostMapping("/matching/routes")
    public ResponseEntity<RouteEstimate> preView(@RequestBody RouteRequest req) {
        return ResponseEntity.ok(service.previewRoute(req.departureLat(), req.departureLng(), req.destinationLat(), req.destinationLng()));
    }

    @Operation(summary = "배정 기사 차량 정보", description = "좌석·번호판·차종. 배정 전이면 409")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "차량 요약"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND / ASSIGNED_DRIVER_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "DRIVER_NOT_ASSIGNED")})
    @GetMapping("/matching/rooms/{partyId}/driver")
    public ResponseEntity<DriverSummary> findDriverSummary(@PathVariable Long partyId) {
        return ResponseEntity.ok(service.getAssignDriver(partyId));
    }

    // 같은 경로의 전체 목록 조회와 쿼리 파라미터 유무로 구분한다 (params 없이 두 개면 Ambiguous mapping으로 기동 실패)
    @Operation(summary = "지도 영역 안 방 목록", description = "화면 뷰포트의 남서(sw)·북동(ne) 좌표로 출발지가 영역 안인 ACTIVE 방 조회")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 목록"), @ApiResponse(responseCode = "400", description = "INVALID_MAP_BOUNDS - sw 가 ne 보다 큼")})
    @GetMapping(value = "/matching/rooms", params = {"swLat", "swLng", "neLat", "neLng"})
    public ResponseEntity<PartyListResponse> listWithin(@RequestParam Double swLat,
                                                        @RequestParam Double swLng,
                                                        @RequestParam Double neLat,
                                                        @RequestParam Double neLng) {
        return ResponseEntity.ok(PartyListResponse.from(service.findActivePartiesWithin(swLat, swLng, neLat, neLng)));
    }
}
