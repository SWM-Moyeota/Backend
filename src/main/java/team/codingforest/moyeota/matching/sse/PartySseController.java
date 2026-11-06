package team.codingforest.moyeota.matching.sse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "매칭방")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PartySseController {
    private final PartySseService partySseService;

    @Operation(summary = "방 변화 구독(SSE)", description = """
            연결하면 `connected` 에 방 상세 전체(GET /matching/rooms/{partyId} 와 같은 모양)가 온다. 재연결 때도 같다.
            이후 변경분만 온다. closed 를 뺀 모든 이벤트와 방 상세에 `version` 이 있다 - 참여·나가기·모집 마감·약속 확정·출발지 도착·동승 종료(출발) 때 1 오른다.
            연결 중에 방이 바뀌면 변경분이 connected 보다 먼저 올 수 있다.
            - `member-joined`: { member, status, version }. member 는 방 상세 members 원소와 같은 모양(host·arrived 는 항상 false - 새로 들어온 사람은 방장이 아니고 아직 도착 전이다)
            - `member-left`: { publicId, status, hostPublicId, version }. hostPublicId 는 이 이벤트를 반영한 뒤의 방장 - 받을 때마다 members 의 host 표시를 이 값으로 다시 지정한다. null 이거나 목록에 없는 publicId 면 방 상세를 다시 받는다
            - `recruiting-closed`: { status, version }. 모집 시간이 끝나 지금 인원(2명 이상)으로 모집이 마감됨(status 는 COMPLETED)
            - `confirmed`: { status, departureAt, version }. 방장이 약속 시간을 확정함. departureAt 은 ISO-8601 약속 시간이고 장소는 방의 출발지. 모집도 끝나 status 는 COMPLETED. 이후 참여·나가기가 막히고 방 상세의 confirmed 도 true
            - `member-arrived`: { publicId, version }. 멤버가 출발지에 도착함 - members 에서 그 publicId 의 arrived 를 true 로 바꾼다. publicId 가 null 이거나 목록에 없으면 방 상세를 다시 받는다
            - `closed`: { status }. 방이 닫힘(status 는 FINISHED 또는 CANCELED - 누가 출발을 눌러 끝난 방도 FINISHED). 받으면 종료로 처리한다. 서버가 연결을 끊는다
            version 규칙: 받은 version 이 내 version+1 이면 반영, 같거나 작으면 버림, 그보다 크면 GET /matching/rooms/{partyId} 로 다시 받아 통째로 교체(스트림은 유지).
            connected 와 다시 받은 상세는 내 version 이 없거나 그 이상일 때만 통째로 교체하고, 낮으면 버린다. connected 전에 온 변경분은 틈으로 보고 상세를 다시 받는다.
            version 이 없는 응답(이전 서버)이면 이미 있는 publicId 의 member-joined, 없는 publicId 의 member-left 는 목록은 그대로 두고 상태만 반영한다.
            기본 10초마다 `:ping` 주석(연결 유지 - 중간 프록시가 조용한 연결을 끊지 않게 하는 것이 목적이라 주기는 서버 설정이고 바뀔 수 있다. 앱은 이걸 「살아있음」 판정에 쓰지 말고 읽기 타임아웃으로만 볼 것). 45초 동안 아무것도 안 오면 끊긴 것으로 보고 3초 뒤 재연결한다. 방을 나가면 서버가 그 사람의 연결을 닫는다(이벤트 없이 스트림 종료). 같은 방에 다시 연결하면 이전 연결은 닫힌다""")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "text/event-stream"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND")})
    @GetMapping(value = "/matching/rooms/{partyId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long partyId, @CurrentUser Long memberId) {
        return partySseService.subscribe(partyId, memberId);
    }
}
