package team.codingforest.moyeota.chat.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.chat.application.MessageSearchService;
import team.codingforest.moyeota.chat.application.dto.MessageSearchPage;
import team.codingforest.moyeota.chat.domain.search.MessageSearchQuery;
import team.codingforest.moyeota.user.api.CurrentUser;
import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat-rooms/{chatRoomId}/messages/search/advanced")
public class MessageSearchController {
    private final MessageSearchService service;

    @GetMapping
    public MessageSearchPage search(@CurrentUser Long userId, @PathVariable Long chatRoomId,
            @RequestParam String keyword, @RequestParam(required = false) Long senderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "30") int size) {
        return service.search(userId, new MessageSearchQuery(chatRoomId, keyword, senderId, from, until, cursor, size));
    }
}
