package team.codingforest.moyeota.matching.party;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.matching.party.dto.ArriveRequest;
import team.codingforest.moyeota.matching.party.dto.ConfirmPartyRequest;
import team.codingforest.moyeota.matching.party.dto.OpenPartyRequest;
import team.codingforest.moyeota.matching.party.dto.OpenPartyResponse;
import team.codingforest.moyeota.matching.party.dto.OpenReservationRequest;
import team.codingforest.moyeota.matching.party.dto.ReservationListResponse;
import team.codingforest.moyeota.matching.party.dto.ReservationSearchResponse;
import team.codingforest.moyeota.matching.party.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.party.dto.PartyListResponse;
import team.codingforest.moyeota.matching.party.dto.PartyResult;
import team.codingforest.moyeota.matching.party.dto.PartyStatusResponse;
import team.codingforest.moyeota.matching.party.dto.MatchCandidate;
import team.codingforest.moyeota.matching.party.dto.RealTimeMatchingRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "매칭방", description = "동승 매칭방 생성·참여·조회. status 는 ACTIVE(모집 중 - 정원이 차도 ACTIVE), COMPLETED(방장이 약속을 확정한 실시간 방), FINISHED(동승 끝), CANCELED")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PartyController {
    private final PartyService partyService;

    @Operation(summary = "방 생성", description = "경로·예상 요금을 네이버로 계산해 저장. 생성자는 자동 참여. 진행 중인 방이 이미 있으면 409. 만든 지 15분이 지나도 혼자면 서버가 취소한다. body 의 creatorId 는 무시(토큰 사용)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "생성된 방"), @ApiResponse(responseCode = "400", description = "SAME_DEPARTURE_DESTINATION / INVALID_CAPACITY / OUT_OF_SERVICE_AREA"), @ApiResponse(responseCode = "409", description = "ALREADY_JOINED_OTHER_PARTY"), @ApiResponse(responseCode = "502", description = "ROUTE_SEARCH_FAILED - 네이버 경로 API 장애")})
    @PostMapping("/matching/rooms")
    public ResponseEntity<OpenPartyResponse> open(@CurrentUser Long memberId, @Valid @RequestBody OpenPartyRequest request) {
        PartyResult party = partyService.create(request.toCommand(memberId));

        return ResponseEntity.ok(OpenPartyResponse.from(party));
    }

    @Operation(summary = "방 참여", description = "모집 중(ACTIVE)이고 자리가 남은 방에 들어간다. 정원이 차도 status 는 ACTIVE 그대로이고 다음 참여만 PARTY_FULL 로 막힌다. 방장이 약속을 확정한 방(COMPLETED)과 출발 시각이 지난 예약 방은 PARTY_CLOSED")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "참여 후 상태·인원. 동승자 목록은 SSE 구독(/events)의 connected 로 받는다"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_FULL / PARTY_CLOSED / ALREADY_JOINED_PARTY / ALREADY_JOINED_OTHER_PARTY")})
    @PostMapping("/matching/rooms/{partyId}/join")
    public ResponseEntity<PartyStatusResponse> join(@PathVariable Long partyId, @CurrentUser Long memberId) {
        return ResponseEntity.ok(partyService.join(partyId, memberId));
    }

    @Operation(summary = "방 나가기", description = "닫히지 않은 방(ACTIVE/COMPLETED)이면 약속이 확정된 뒤에도, 예약 방에서도 언제든 나갈 수 있다. 방장이 나가면 남은 멤버 중 가장 먼저 합류한 사람이 승계한다. 마지막 멤버가 나가면 CANCELED. 약속을 확정한 실시간 방(COMPLETED)에서 나가 1명만 남아도 CANCELED - 더 모을 수 없고 혼자서는 출발할 수 없다. 예약 방은 혼자 남아도 출발 전까지 ACTIVE 로 다시 모은다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "나감"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_CLOSED - 이미 끝났거나 취소된 방")})
    @DeleteMapping("/matching/leave/{partyId}")
    public ResponseEntity<Void> leave(@PathVariable Long partyId, @CurrentUser Long memberId) {
        partyService.leave(partyId, memberId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "방 상세", description = "members 에 동승자 publicId·닉네임·이미지·탑승 횟수·host(방장 여부, 합류 순 정렬). 방장 = 방을 만든 사람이고, 방장이 나가면 남은 멤버 중 가장 먼저 합류한 사람이 승계한다. 멤버가 있는 방에는 항상 정확히 한 명. 본인 판별은 publicId 와 토큰 sub 비교. departureAt 은 예약 방의 출발 시각이거나 실시간 방에서 방장이 확정한 약속 시간(확정 전 null). confirmed 는 약속이 정해진 방인지 - 예약 방은 항상 true, 실시간 방은 방장이 확정한 뒤 true. 확정 뒤에도 나갈 수는 있다. 실시간 방의 출발지(departureLat·departureLng·departure)는 확정 때 만남 장소로 바뀔 수 있으니 confirmed 뒤에는 방 상세를 다시 받는다. members[].arrived 는 출발지 도착(POST /matching/rooms/{partyId}/arrive)을 마친 멤버인지(약속이 정해지기 전에는 늘 false). SSE 구독(/events)의 connected 이벤트도 같은 모양이다. version 은 참여·나가기·약속 확정·출발지 도착·동승 종료(출발) 때 1 오르는 번호 - SSE 이벤트의 version 과 비교해 놓친 변경을 알아챈다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 상세"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND")})
    @GetMapping("/matching/rooms/{partyId}")
    public ResponseEntity<PartyDetailResult> detail(@PathVariable Long partyId) {
        return ResponseEntity.ok(partyService.getPartyDetail(partyId));
    }

    @Operation(summary = "모집 중인 방 전체 목록", description = "파라미터 없이 호출하면 ACTIVE 전체(정원이 찬 방 포함). 지도 화면은 아래 영역 조회를 사용")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 목록")})
    @GetMapping("/matching/rooms")
    public ResponseEntity<PartyListResponse> list() {
        return ResponseEntity.ok(PartyListResponse.from(partyService.findActiveParties()));
    }

    // 같은 경로의 전체 목록 조회와 쿼리 파라미터 유무로 구분한다 (params 없이 두 개면 Ambiguous mapping으로 기동 실패)
    @Operation(summary = "지도 영역 안 방 목록", description = "화면 뷰포트의 남서(sw)·북동(ne) 좌표로 출발지가 영역 안이고 자리가 남은 ACTIVE 실시간 방 조회")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 목록"), @ApiResponse(responseCode = "400", description = "INVALID_MAP_BOUNDS - sw 가 ne 보다 큼")})
    @GetMapping(value = "/matching/rooms", params = {"swLat", "swLng", "neLat", "neLng"})
    public ResponseEntity<PartyListResponse> listWithin(@RequestParam Double swLat,
                                                        @RequestParam Double swLng,
                                                        @RequestParam Double neLat,
                                                        @RequestParam Double neLng) {
        return ResponseEntity.ok(PartyListResponse.fromSummaries(partyService.findActivePartiesWithin(swLat, swLng, neLat, neLng)));
    }

    @Operation(summary = "약속 확정", description = "실시간 방의 방장이 채팅으로 정한 약속 시간과 만남 장소를 확정한다. 약속 시간은 확정하는 지금부터 5분 뒤에서 1시간 사이(앱의 가장 짧은 선택지는 시계 차이를 감안해 5분보다 조금 넉넉하게). 만남 장소(departureLat·departureLng·departure)는 선택이고 셋을 함께 보내면 방의 출발지가 그 자리로 바뀌어 도착 판정(출발지 100m)의 기준이 된다. 비우면 출발지는 그대로다. 멤버가 2명 이상인 ACTIVE 방에서 한 번만 할 수 있다. 확정하면 COMPLETED 가 되어 더는 참여할 수 없다(나가기는 된다). 모든 구독자에게 SSE confirmed { status, departureAt, departureLat, departureLng, departure, version } 로 알린다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "확정됨"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - departureAt 누락·형식 오류 / INVALID_MEETING_TIME - 지금부터 5분 뒤에서 1시간 사이가 아님 / INCOMPLETE_MEETING_PLACE - 만남 장소의 좌표·이름 중 일부만 보냄 / OUT_OF_SERVICE_AREA / SAME_DEPARTURE_DESTINATION - 만남 장소가 도착지와 같음"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER / NOT_PARTY_HOST"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_CLOSED / NOT_REALTIME_PARTY / ALREADY_CONFIRMED / NOT_ENOUGH_MEMBERS")})
    @PostMapping("/matching/rooms/{partyId}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long partyId, @CurrentUser Long memberId, @Valid @RequestBody ConfirmPartyRequest request) {
        partyService.confirm(partyId, memberId, request.toPlan());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "출발지 도착", description = "약속 시간(방 상세 departureAt) 10분 전부터 방의 출발지(departureLat·departureLng) 반경(실시간 방 100m, 예약 방 400m) 안에서 누른다. 지금 위치를 보내면 서버가 PostGIS(ST_DWithin, 타원체 위 거리)로 그 반경 안인지 재고 도착 시각을 남긴다.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "도착 처리됨(이미 도착했어도 204)"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - latitude·longitude 누락·형식 오류 / OUT_OF_SERVICE_AREA - 서비스 지역 밖 좌표 / TOO_FAR_FROM_DEPARTURE - 출발지에서 100m 넘게 떨어짐"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_CLOSED - 이미 끝났거나(누가 먼저 출발함) 취소된 방 / PARTY_NOT_CONFIRMED - 약속 시간을 확정하지 않은 실시간 방 / ARRIVAL_NOT_OPEN - 약속 시간 10분 전보다 이름")})
    @PostMapping("/matching/rooms/{partyId}/arrive")
    public ResponseEntity<Void> arrive(@PathVariable Long partyId, @CurrentUser Long memberId, @Valid @RequestBody ArriveRequest request) {
        partyService.arrive(partyId, memberId, request.latitude(), request.longitude());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "출발", description = "출발지에 도착(arrive)한 방장이 약속 시간 뒤에 누르면 방이 바로 끝난다(FINISHED). 본문 없음. 도착한 멤버만 탄 사람으로 남고, 도착하지 않은 멤버는 탑승 기록·정산에서 빠질 뿐 불이익은 없다. 약속이 정해진 방(확정한 실시간 방, 모든 예약 방)이고 닫히지 않은 방, 멤버 2명 이상에서만 된다. 끝나면 탑승 기록이 남고 모든 구독자에게 SSE closed { status: FINISHED } 를 보낸다(서버가 연결을 끊는다). 사용자가 방을 끝내는 길은 이것 하나다. 아무도 누르지 않으면 약속 시간에서 1일 뒤(약속이 없는 실시간 방은 만든 지 1일 뒤) 서버가 정리한다 - 도착한 멤버가 있으면 FINISHED(탑승 기록 남음), 없으면 CANCELED")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "방이 끝남"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER / NOT_PARTY_HOST - 방장만 누른다"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PARTY_CLOSED - 이미 끝났거나 취소된 방 / PARTY_NOT_CONFIRMED - 약속을 확정하지 않은 실시간 방 / NOT_ENOUGH_MEMBERS - 멤버가 1명뿐인 방 / MEETING_NOT_STARTED - 약속 시간 전 / NOT_ARRIVED - 방장이 아직 도착 전")})
    @PostMapping("/matching/rooms/{partyId}/depart")
    public ResponseEntity<Void> depart(@PathVariable Long partyId, @CurrentUser Long memberId) {
        partyService.depart(partyId, memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "예약 방 생성", description = "출발 시각을 정해 방을 만든다. 경로·예상 요금·예상 시간은 비어 있다. 참여·나가기·상세·SSE 는 기존 방 API 를 쓴다. 실시간 방에 있거나 출발 시각 2시간 안에 다른 예약 방이 있으면 409. 출발 시각까지 혼자면 서버가 취소한다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "생성된 방"), @ApiResponse(responseCode = "400", description = "SAME_DEPARTURE_DESTINATION / INVALID_CAPACITY / OUT_OF_SERVICE_AREA / INVALID_DEPARTURE_TIME"), @ApiResponse(responseCode = "409", description = "ALREADY_JOINED_OTHER_PARTY / PARTY_TIME_CONFLICT")})
    @PostMapping("/matching/reservations")
    public ResponseEntity<OpenPartyResponse> reserve(@CurrentUser Long memberId, @Valid @RequestBody OpenReservationRequest request) {
        return ResponseEntity.ok(OpenPartyResponse.from(partyService.reserve(request.toCommand(memberId))));
    }

    @Operation(summary = "예약 방 목록", description = "출발 시각이 기간 안이고 자리가 남은 ACTIVE 예약 방을 출발 시각 순으로 조회. 이미 출발한 방은 빠진다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 목록"), @ApiResponse(responseCode = "400", description = "INVALID_TIME_RANGE - from 이 to 보다 늦거나 같음")})
    @GetMapping("/matching/reservations")
    public ResponseEntity<ReservationListResponse> listReservations(@RequestParam Instant from, @RequestParam Instant to) {
        return ResponseEntity.ok(ReservationListResponse.from(partyService.findReservations(from, to)));
    }

    @Operation(summary = "곧 출발 예약 방 목록", description = "화면 뷰포트의 남서(sw)·북동(ne) 좌표 안에서 30분 안에 출발하고 자리가 남은 ACTIVE 예약 방을 출발 시각 순으로 조회")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 목록"), @ApiResponse(responseCode = "400", description = "INVALID_MAP_BOUNDS - sw 가 ne 보다 큼")})
    @GetMapping("/matching/reservations/soon")
    public ResponseEntity<ReservationListResponse> listSoonReservations(@RequestParam Double swLat,
                                                                        @RequestParam Double swLng,
                                                                        @RequestParam Double neLat,
                                                                        @RequestParam Double neLng) {
        return ResponseEntity.ok(ReservationListResponse.from(partyService.findSoonReservationsWithin(swLat, swLng, neLat, neLng)));
    }

    @Operation(summary = "예약 방 검색", description = "출발지(depLat·depLng) 1km 안에서 date(한국 날짜 yyyy-MM-dd) 하루 동안 출발하고 자리가 남은 ACTIVE 예약 방을 최대 100개 조회. date 는 오늘부터 7일 뒤까지 고를 수 있고, 오늘이면 이미 출발한 방은 빠진다. 도착지(destLat·destLng)는 선택이며 둘 다 보내거나 둘 다 뺀다. 도착지를 보내면 방마다 destinationNearby(도착지도 1km 안인지)가 붙고 true 인 방이 먼저, 그 안에서 출발 시각 순이다. 도착지를 빼면 출발 시각 순이고 destinationNearby 는 null")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 목록(없으면 빈 목록)"), @ApiResponse(responseCode = "400", description = "depLat·depLng·date 누락 / INVALID_REQUEST - 숫자·날짜 형식 오류 / INCOMPLETE_DESTINATION - destLat·destLng 중 하나만 보냄 / OUT_OF_SERVICE_AREA - 서비스 지역 밖 좌표 / INVALID_SEARCH_DATE - date 가 오늘부터 7일 뒤 밖")})
    @GetMapping("/matching/reservations/search")
    public ResponseEntity<ReservationSearchResponse> searchReservations(@RequestParam Double depLat,
                                                                        @RequestParam Double depLng,
                                                                        @RequestParam(required = false) Double destLat,
                                                                        @RequestParam(required = false) Double destLng,
                                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ReservationSearchResponse.from(partyService.searchReservations(depLat, depLng, destLat, destLng, date)));
    }

    @Operation(summary = "실시간 매칭 후보", description = "출발지·도착지 반경 300m 안의 자리가 남은 ACTIVE 실시간 방을 도보 시간 합(탑승지까지 + 하차지에서 목적지까지) 오름차순으로 반환")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "후보 목록(없으면 빈 배열)"), @ApiResponse(responseCode = "400", description = "OUT_OF_SERVICE_AREA - 좌표 누락 또는 서비스 지역 밖"), @ApiResponse(responseCode = "409", description = "ALREADY_JOINED_OTHER_PARTY"), @ApiResponse(responseCode = "502", description = "ROUTE_SEARCH_FAILED - OSRM 장애")})
    @PostMapping("/matching/rooms/realtime")
    public ResponseEntity<List<MatchCandidate>> getListCandidate(@CurrentUser Long memberId, @RequestBody RealTimeMatchingRequest request) {
        return ResponseEntity.ok(partyService.realtimeMatch(memberId, request.toCommand()));
    }
}
