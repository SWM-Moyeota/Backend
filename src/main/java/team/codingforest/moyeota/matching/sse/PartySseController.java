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

    @Operation(summary = "방 변화 구독(SSE)", description = "멤버 변화 시 `changed`, 방이 닫히면 `closed` 이벤트. 데이터는 partyId 뿐이라 받으면 상세를 다시 조회한다. 5초마다 heartbeat 주석. 방을 나가면 서버가 그 사람의 연결을 닫는다(이벤트 없이 스트림 종료)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "text/event-stream"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND")})
    @GetMapping(value = "/matching/rooms/{partyId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long partyId, @CurrentUser Long memberId) {
        return partySseService.subscribe(partyId, memberId);
    }
}
