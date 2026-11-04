package team.codingforest.moyeota.matching.party;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import team.codingforest.moyeota.matching.party.dto.ArriveRequest;
import team.codingforest.moyeota.matching.party.dto.ConfirmPartyRequest;
import team.codingforest.moyeota.matching.party.dto.OpenPartyRequest;
import team.codingforest.moyeota.matching.party.dto.OpenPartyResponse;
import team.codingforest.moyeota.matching.party.dto.OpenReservationRequest;
import team.codingforest.moyeota.matching.party.dto.ReservationListResponse;
import team.codingforest.moyeota.matching.party.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.party.dto.PartyListResponse;
import team.codingforest.moyeota.matching.party.dto.PartyResult;
import team.codingforest.moyeota.matching.party.dto.PartyStatusResponse;
import team.codingforest.moyeota.matching.party.dto.MatchCandidate;
import team.codingforest.moyeota.matching.party.dto.RealTimeMatchingRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

import java.time.Instant;
import java.util.List;

@Tag(name = "매칭방", description = "동승 매칭방 생성·참여·조회. 정원이 차면 자동으로 매칭(기사 콜) 시작")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PartyController {
    private final PartyService partyService;

    @Operation(summary = "방 생성", description = "경로·예상 요금을 네이버로 계산해 저장. 생성자는 자동 참여. 반경은 서버 고정값(500m). 진행 중인 방이 이미 있으면 409. body 의 creatorId 는 무시(토큰 사용)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "생성된 방"), @ApiResponse(responseCode = "400", description = "SAME_DEPARTURE_DESTINATION / INVALID_CAPACITY / OUT_OF_SERVICE_AREA"), @ApiResponse(responseCode = "409", description = "ALREADY_JOINED_OTHER_PARTY"), @ApiResponse(responseCode = "502", description = "ROUTE_SEARCH_FAILED - 네이버 경로 API 장애")})
    @PostMapping("/matching/rooms")
    public ResponseEntity<OpenPartyResponse> open(@CurrentUser Long memberId, @Valid @RequestBody OpenPartyRequest request) {
        PartyResult party = partyService.open(request.toCommand(memberId));

        return ResponseEntity.ok(OpenPartyResponse.from(party));
    }

    @Operation(summary = "방 참여", description = "정원이 차면 COMPLETED → MATCHING 으로 넘어가며 기사 콜이 시작되고 채팅방이 열린다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "참여 후 상태·인원. 동승자 목록은 SSE 구독(/events)의 connected 로 받는다"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_FULL / PARTY_CLOSED / ALREADY_JOINED_PARTY / ALREADY_JOINED_OTHER_PARTY")})
    @PostMapping("/matching/rooms/{partyId}/join")
    public ResponseEntity<PartyStatusResponse> join(@PathVariable Long partyId, @CurrentUser Long memberId) {
        return ResponseEntity.ok(partyService.join(partyId, memberId));
    }

    @Operation(summary = "방 나가기", description = "모집 중(ACTIVE/COMPLETED)이고 약속이 확정되지 않은 방에서만 가능. 약속이 확정된 방(방장이 약속 시간을 확정한 실시간 방, 그리고 모든 예약 방 - 참여한 순간부터)은 방장도 나갈 수 없다. 방장이 나가면 남은 멤버 중 가장 먼저 합류한 사람이 승계한다. 마지막 멤버가 나가면 방은 CANCELED")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "나감"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_NOT_RECRUITING - 매칭 시작 후 / CANNOT_LEAVE_CONFIRMED_PARTY - 약속이 확정된 방(확정한 실시간 방, 모든 예약 방)")})
    @DeleteMapping("/matching/leave/{partyId}")
    public ResponseEntity<Void> leave(@PathVariable Long partyId, @CurrentUser Long memberId) {
        partyService.leave(partyId, memberId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "방 상세", description = "members 에 동승자 publicId·닉네임·이미지·탑승 횟수·host(방장 여부, 합류 순 정렬). 방장 = 방을 만든 사람이고, 방장이 나가면 남은 멤버 중 가장 먼저 합류한 사람이 승계한다(나가기는 약속 확정 전에만 되므로 승계도 확정 전에만 일어난다). 멤버가 있는 방에는 항상 정확히 한 명. 본인 판별은 publicId 와 토큰 sub 비교. departureAt 은 예약 방의 출발 시각이거나 실시간 방에서 방장이 확정한 약속 시간(확정 전 null). confirmed 는 약속이 정해진 방인지 - 예약 방은 항상 true, 실시간 방은 방장이 확정한 뒤 true. true 면 아무도 나갈 수 없다. members[].arrived 는 출발지 도착(POST /matching/rooms/{partyId}/arrive)을 마친 멤버인지(약속이 정해지기 전에는 늘 false). SSE 구독(/events)의 connected 이벤트도 같은 모양이다. version 은 참여·나가기·모집 마감·약속 확정·출발지 도착·합승 종료(출발) 때 1 오르는 번호 - SSE 이벤트의 version 과 비교해 놓친 변경을 알아챈다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 상세"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND")})
    @GetMapping("/matching/rooms/{partyId}")
    public ResponseEntity<PartyDetailResult> detail(@PathVariable Long partyId) {
        return ResponseEntity.ok(partyService.getPartyDetail(partyId));
    }

    @Operation(summary = "모집 중인 방 전체 목록", description = "파라미터 없이 호출하면 ACTIVE 전체. 지도 화면은 아래 영역 조회를 사용")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 목록")})
    @GetMapping("/matching/rooms")
    public ResponseEntity<PartyListResponse> list() {
        return ResponseEntity.ok(PartyListResponse.from(partyService.findActiveParties()));
    }

    @Operation(summary = "배정 기사 차량 정보", description = "좌석·번호판·차종. 배정 전이면 409")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "차량 요약"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND / ASSIGNED_DRIVER_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "DRIVER_NOT_ASSIGNED")})
    @GetMapping("/matching/rooms/{partyId}/driver")
    public ResponseEntity<DriverSummary> findDriverSummary(@PathVariable Long partyId) {
        return ResponseEntity.ok(partyService.getAssignDriver(partyId));
    }

    // 같은 경로의 전체 목록 조회와 쿼리 파라미터 유무로 구분한다 (params 없이 두 개면 Ambiguous mapping으로 기동 실패)
    @Operation(summary = "지도 영역 안 방 목록", description = "화면 뷰포트의 남서(sw)·북동(ne) 좌표로 출발지가 영역 안인 ACTIVE 방 조회")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 목록"), @ApiResponse(responseCode = "400", description = "INVALID_MAP_BOUNDS - sw 가 ne 보다 큼")})
    @GetMapping(value = "/matching/rooms", params = {"swLat", "swLng", "neLat", "neLng"})
    public ResponseEntity<PartyListResponse> listWithin(@RequestParam Double swLat,
                                                        @RequestParam Double swLng,
                                                        @RequestParam Double neLat,
                                                        @RequestParam Double neLng) {
        return ResponseEntity.ok(PartyListResponse.fromSummaries(partyService.findActivePartiesWithin(swLat, swLng, neLat, neLng)));
    }

    @Operation(summary = "합승 종료(약속 확정 전 방)", description = "약속 시간을 확정하지 않은 방을 끝내는 이전 경로(구버전 앱용). 기사 배정 없이 합승만 하고 끝났을 때 방장이 직접 호출. 정원이 찬(COMPLETED) 방에서만 가능. 노쇼 기록 없이 멤버 전원이 탄 것으로 탑승 기록이 남는다. 약속이 확정된 방(방장이 약속 시간을 확정한 실시간 방, 모든 예약 방)은 409 USE_DEPART_FOR_CONFIRMED_PARTY - 출발(POST /matching/rooms/{partyId}/depart)로만 끝낸다. 기사 기능이 켜진 모드에선 COMPLETED 가 순간이라 사실상 호출할 일이 없다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "종료됨"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER / NOT_PARTY_HOST"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "USE_DEPART_FOR_CONFIRMED_PARTY - 약속이 확정된 방 / PARTY_NOT_COMPLETED / DRIVER_ALREADY_ASSIGNED")})
    @PostMapping("/matching/rooms/{partyId}/finish")
    public ResponseEntity<Void> finish(@PathVariable Long partyId, @CurrentUser Long memberId) {
        partyService.finish(partyId, memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "약속 시간 확정", description = "실시간 방의 방장이 채팅으로 정한 약속 시간을 확정한다. 장소는 늘 방의 출발지라 시간만 보낸다. 약속 시간은 확정하는 지금부터 1시간 안(지난 시각 불가). 멤버가 2명 이상이고 모집 중(ACTIVE/COMPLETED)일 때 한 번만 할 수 있다. 확정하면 모집이 끝나(ACTIVE → COMPLETED, 정원이 차 이미 COMPLETED 면 그대로) 더는 참여할 수 없고, 방장을 포함해 아무도 나갈 수 없다. 모든 구독자에게 SSE confirmed { status, departureAt, version } 로 알린다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "확정됨"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - departureAt 누락·형식 오류 / INVALID_MEETING_TIME - 지난 시각이거나 지금부터 1시간 넘게 뒤"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER / NOT_PARTY_HOST"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "NOT_REALTIME_PARTY / ALREADY_CONFIRMED / PARTY_NOT_RECRUITING / NOT_ENOUGH_MEMBERS")})
    @PostMapping("/matching/rooms/{partyId}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long partyId, @CurrentUser Long memberId, @Valid @RequestBody ConfirmPartyRequest request) {
        partyService.confirm(partyId, memberId, request.departureAt());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "출발지 도착", description = "약속 시간(방 상세 departureAt)이 된 뒤 방의 출발지(departureLat·departureLng) 100m 안에서 누른다. 지금 위치를 보내면 서버가 PostGIS(ST_DWithin, 타원체 위 거리)로 출발지 100m 안인지 재고 도착 시각을 남긴다. 약속이 확정된 방(방장이 약속 시간을 확정한 실시간 방, 모든 예약 방)이고 닫히지 않은(ACTIVE/COMPLETED) 방의 멤버만 할 수 있다. 이미 도착한 사람이 다시 누르면 처음 시각을 그대로 두고 204(위치·시간을 다시 보지 않고 version·SSE 도 없다). 처음 도착하면 version 이 1 오르고 방 상세의 members[].arrived 가 true 가 되며, 모든 구독자에게 SSE member-arrived { publicId, version } 를 보낸다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "도착 처리됨(이미 도착했어도 204)"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - latitude·longitude 누락·형식 오류 / OUT_OF_SERVICE_AREA - 서비스 지역 밖 좌표 / TOO_FAR_FROM_DEPARTURE - 출발지에서 100m 넘게 떨어짐"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_CLOSED - 이미 끝났거나(누가 먼저 출발함) 취소된 방 / PARTY_NOT_CONFIRMED - 약속 시간을 확정하지 않은 실시간 방 / MEETING_NOT_STARTED - 약속 시간 전")})
    @PostMapping("/matching/rooms/{partyId}/arrive")
    public ResponseEntity<Void> arrive(@PathVariable Long partyId, @CurrentUser Long memberId, @Valid @RequestBody ArriveRequest request) {
        partyService.arrive(partyId, memberId, request.latitude(), request.longitude());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "출발", description = "출발지에 도착(arrive)한 멤버가 약속 시간 뒤에 누르면 방이 바로 끝난다(FINISHED). 방장이 아니어도 된다. 본문 없음. 그 순간까지 도착하지 않은 멤버는 노쇼로 기록되어 탑승 기록·탑승 횟수에서 빠지고, 도착한 멤버만 탄 사람으로 남는다. 약속이 확정된 방(방장이 약속 시간을 확정한 실시간 방, 모든 예약 방)이고 닫히지 않은(ACTIVE/COMPLETED) 기사 없는 방, 멤버 2명 이상에서만 된다. 끝나면 탑승 기록이 남고 모든 구독자에게 SSE closed { status: FINISHED } 를 보낸다(서버가 연결을 끊는다). 약속이 확정된 방을 끝내는 길은 이것 하나다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "방이 끝남"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_CLOSED - 이미 끝났거나(누가 먼저 출발함) 취소된 방 / PARTY_NOT_CONFIRMED - 약속 시간을 확정하지 않은 실시간 방 / DRIVER_ALREADY_ASSIGNED / NOT_ENOUGH_MEMBERS - 멤버가 1명뿐인 예약 방 / MEETING_NOT_STARTED - 약속 시간 전 / NOT_ARRIVED - 누른 사람이 아직 도착 전")})
    @PostMapping("/matching/rooms/{partyId}/depart")
    public ResponseEntity<Void> depart(@PathVariable Long partyId, @CurrentUser Long memberId) {
        partyService.depart(partyId, memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "예약 방 생성", description = "출발 시각을 정해 방을 만든다. 경로·예상 요금·예상 시간은 비어 있다. 참여·나가기·상세·SSE 는 기존 방 API 를 쓴다. 실시간 방에 있거나 출발 시각 2시간 안에 다른 예약 방이 있으면 409")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "생성된 방"), @ApiResponse(responseCode = "400", description = "SAME_DEPARTURE_DESTINATION / INVALID_CAPACITY / OUT_OF_SERVICE_AREA / INVALID_DEPARTURE_TIME"), @ApiResponse(responseCode = "409", description = "ALREADY_JOINED_OTHER_PARTY / PARTY_TIME_CONFLICT / RESERVATION_NOT_SUPPORTED")})
    @PostMapping("/matching/reservations")
    public ResponseEntity<OpenPartyResponse> reserve(@CurrentUser Long memberId, @Valid @RequestBody OpenReservationRequest request) {
        return ResponseEntity.ok(OpenPartyResponse.from(partyService.reserve(request.toCommand(memberId))));
    }

    @Operation(summary = "예약 방 목록", description = "출발 시각이 기간 안인 ACTIVE 예약 방을 출발 시각 순으로 조회")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 목록"), @ApiResponse(responseCode = "400", description = "INVALID_TIME_RANGE - from 이 to 보다 늦거나 같음")})
    @GetMapping("/matching/reservations")
    public ResponseEntity<ReservationListResponse> listReservations(@RequestParam Instant from, @RequestParam Instant to) {
        return ResponseEntity.ok(ReservationListResponse.from(partyService.findReservations(from, to)));
    }

    @Operation(summary = "곧 출발 예약 방 목록", description = "화면 뷰포트의 남서(sw)·북동(ne) 좌표 안에서 30분 안에 출발하는 ACTIVE 예약 방을 출발 시각 순으로 조회")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 목록"), @ApiResponse(responseCode = "400", description = "INVALID_MAP_BOUNDS - sw 가 ne 보다 큼")})
    @GetMapping("/matching/reservations/soon")
    public ResponseEntity<ReservationListResponse> listSoonReservations(@RequestParam Double swLat,
                                                                        @RequestParam Double swLng,
                                                                        @RequestParam Double neLat,
                                                                        @RequestParam Double neLng) {
        return ResponseEntity.ok(ReservationListResponse.from(partyService.findSoonReservationsWithin(swLat, swLng, neLat, neLng)));
    }

    @Operation(summary = "실시간 매칭 후보", description = "출발지·도착지 반경 300m 안의 ACTIVE 방을 도보 시간 합(탑승지까지 + 하차지에서 목적지까지) 오름차순으로 반환")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "후보 목록(없으면 빈 배열)"), @ApiResponse(responseCode = "400", description = "OUT_OF_SERVICE_AREA - 좌표 누락 또는 서비스 지역 밖"), @ApiResponse(responseCode = "409", description = "ALREADY_JOINED_OTHER_PARTY"), @ApiResponse(responseCode = "502", description = "ROUTE_SEARCH_FAILED - OSRM 장애")})
    @PostMapping("/matching/rooms/realtime")
    public ResponseEntity<List<MatchCandidate>> getListCandidate(@CurrentUser Long memberId, @RequestBody RealTimeMatchingRequest request) {
        return ResponseEntity.ok(partyService.realtimeMatch(memberId, request.toCommand()));
    }
}
