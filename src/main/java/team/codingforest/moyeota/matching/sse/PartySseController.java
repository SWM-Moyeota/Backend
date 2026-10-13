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
            연결 직후 `connected` 에 방 상세 전체(GET /matching/rooms/{partyId} 와 같은 모양). 재연결 때도 같다 - 받으면 화면을 통째로 다시 그린다.
            이후 변경분만 온다.
            - `member-joined`: { member, status }. member 는 방 상세 members 원소와 같은 모양. 이미 있는 publicId 면 목록은 그대로 두고 상태만 반영
            - `member-left`: { publicId, status }. 없는 publicId 면 목록은 그대로 두고 상태만 반영
            - `closed`: 방이 닫힘. 서버가 연결을 끊는다
            30초마다 heartbeat 주석. 90초 동안 아무것도 안 오면 재연결한다. 방을 나가면 서버가 그 사람의 연결을 닫는다(이벤트 없이 스트림 종료). 같은 방에 다시 연결하면 이전 연결은 닫힌다""")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "text/event-stream"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND")})
    @GetMapping(value = "/matching/rooms/{partyId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long partyId, @CurrentUser Long memberId) {
        return partySseService.subscribe(partyId, memberId);
    }
}
