package team.codingforest.moyeota.chat.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.chat.application.ChatReportService;
import team.codingforest.moyeota.chat.application.dto.ReportChatCommand;
import team.codingforest.moyeota.chat.presentation.dto.ChatReportRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

@RestController
@RequestMapping("/api/v1/chat-rooms/{chatRoomId}/reports")
@RequiredArgsConstructor
public class ChatReportController {

    private final ChatReportService chatReportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void report(
            @CurrentUser Long userId,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatReportRequest request
    ) {
        chatReportService.report(new ReportChatCommand(
                chatRoomId, userId, request.chatMessageId(), request.reason(), request.description()));
    }
}