package team.codingforest.moyeota.chat.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.app.dto.ChatMessageResult;
import team.codingforest.moyeota.chat.app.dto.ChatMessageSlice;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatMessageRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;

    @Transactional(readOnly = true)
    public ChatMessageSlice findBefore(Long chatRoomId, Long cursor, int size) {
        List<ChatMessage> messages = chatMessageRepository.findBefore(chatRoomId, cursor, size + 1);

        boolean hasNext = messages.size() > size;
        List<ChatMessage> page = hasNext ? messages.subList(0, size) : messages;
        Long nextCursor = page.isEmpty() ? null : page.getLast().getId();

        return new ChatMessageSlice(
                page.stream().map(ChatMessageResult::from).toList(),
                nextCursor,
                hasNext
        );
    }

}
