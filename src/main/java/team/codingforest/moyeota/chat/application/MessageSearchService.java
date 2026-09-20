package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import team.codingforest.moyeota.chat.application.dto.MessageSearchPage;
import team.codingforest.moyeota.chat.domain.search.*;

@Service
@RequiredArgsConstructor
public class MessageSearchService {
    private final ChatRoomUserService participants;
    private final MessageSearchIndex index;
    private final MessageSearchResultReader reader;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MessageSearchPage search(Long userId, MessageSearchQuery query) {
        participants.validateParticipant(userId, query.roomId());
        return reader.read(userId, query, index.search(query));
    }
}
